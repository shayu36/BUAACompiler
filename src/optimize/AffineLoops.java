package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.instr.*;
import midend.llvm.type.IrBaseType;
import midend.llvm.value.*;
import java.util.*;
import static optimize.IrSupport.*;

/** Summarize proven finite, straight-line i32 loops with affine state transitions. */
public final class AffineLoops {
    private AffineLoops() { }
    private static Instr last(IrBasicBlock b) {
        return b.GetInstrList().isEmpty() ? null : b.GetInstrList().get(b.GetInstrList().size() - 1);
    }

    public static boolean run(IrFunction function) {
        ControlFlow flow = new ControlFlow(function);
        flow.computeDominators();
        Set<IrValue> slots = scalarSlots(function);
        for (IrBasicBlock header : new ArrayList<>(flow.blocks)) {
            if (!(last(header) instanceof BranchInstr branch) ||
                !(branch.GetCond() instanceof CompareInstr compare)) continue;
            IrBasicBlock body = branch.GetTrueBlock(), exit = branch.GetFalseBlock();
            CompareInstr.CompareOp condition = compare.GetCompareOp();
            if (!(last(body) instanceof JumpInstr candidateBack && candidateBack.GetTargetBlock() == header) &&
                last(exit) instanceof JumpInstr reverseBack && reverseBack.GetTargetBlock() == header) {
                IrBasicBlock swap = body; body = exit; exit = swap;
                condition = switch (condition) {
                    case EQ -> CompareInstr.CompareOp.NE; case NE -> CompareInstr.CompareOp.EQ;
                    case SLT -> CompareInstr.CompareOp.SGE; case SGE -> CompareInstr.CompareOp.SLT;
                    case SGT -> CompareInstr.CompareOp.SLE; case SLE -> CompareInstr.CompareOp.SGT;
                    case ULT -> CompareInstr.CompareOp.UGE; case UGE -> CompareInstr.CompareOp.ULT;
                    case UGT -> CompareInstr.CompareOp.ULE; case ULE -> CompareInstr.CompareOp.UGT;
                };
            }
            if (body == header || exit == header || body == exit ||
                !(last(body) instanceof JumpInstr back) || back.GetTargetBlock() != header ||
                flow.predecessors.get(body).size() != 1 || flow.predecessors.get(header).size() != 2) continue;
            IrBasicBlock loopBody = body;
            IrBasicBlock pre = flow.predecessors.get(header).stream().filter(b -> b != loopBody).findFirst().orElse(null);
            if (pre == null || !(last(pre) instanceof JumpInstr enter) || enter.GetTargetBlock() != header) continue;
            List<IrValue> args = compare.GetUseValueList();
            if (!(args.get(0) instanceof LoadInstr indexLoad) || !args.get(0).GetIrType().IsInt32Type()) continue;
            IrValue index = indexLoad.GetUseValueList().get(0), bound = args.get(1);
            if (!slots.contains(index)) continue;
            Set<IrBasicBlock> loop = Set.of(header, body);
            // No SSA result may escape: final stores are the complete observable state.
            boolean escapes = false, headerEscapes = false;
            for (IrBasicBlock b : loop) for (Instr instr : b.GetInstrList()) {
                if (instr.GetUseList().stream().anyMatch(use -> !(use.GetUser() instanceof Instr user) ||
                    !loop.contains(user.GetInBasicBlock()))) {
                    if (b == header) headerEscapes = true; else escapes = true;
                }
            }
            if (escapes) continue;
            LinkedHashMap<IrValue, Form> state = new LinkedHashMap<>();
            Map<IrValue, Form> expressions = new HashMap<>();
            Set<IrValue> written = new LinkedHashSet<>();
            boolean valid = true;
            for (IrBasicBlock b : List.of(header, body)) for (Instr instr : b.GetInstrList()) {
                List<IrValue> operands = instr.GetUseValueList();
                if (instr instanceof BranchInstr || instr instanceof JumpInstr || instr == compare) continue;
                Form form = null;
                if (instr instanceof LoadInstr) {
                    IrValue pointer = operands.get(0);
                    if (!slots.contains(pointer) || !instr.GetIrType().IsInt32Type()) { valid = false; break; }
                    form = state.computeIfAbsent(pointer, Form::variable);
                } else if (instr instanceof StoreInstr) {
                    IrValue pointer = operands.get(1);
                    form = resolve(operands.get(0), expressions, pre, flow);
                    if (b == header || !slots.contains(pointer) || !operands.get(0).GetIrType().IsInt32Type() || form == null) { valid = false; break; }
                    state.put(pointer, form);
                    written.add(pointer);
                    continue;
                } else if (instr instanceof AluInstr alu) {
                    Form a = resolve(operands.get(0), expressions, pre, flow);
                    Form c = resolve(operands.get(1), expressions, pre, flow);
                    if (a != null && c != null && instr.GetIrType().IsInt32Type()) {
                        form = switch (alu.GetOpName()) {
                            case "add" -> a.plus(c, 1);
                            case "sub" -> a.plus(c, -1);
                            case "mul" -> a.isConstant() ? c.scale(a.constant) : c.isConstant() ? a.scale(c.constant) : null;
                            default -> null;
                        };
                    }
                }
                if (form == null) { valid = false; break; }
                expressions.put(instr, form);
            }
            if (!valid || !written.contains(index)) continue;
            Form update = state.get(index);
            if (update.terms.size() != 1 || update.terms.getOrDefault(index, 0) != 1) continue;
            int step = update.constant;
            IrValue startValue = null;
            for (Instr instr : pre.GetInstrList()) if (instr instanceof StoreInstr && instr.GetUseValueList().get(1) == index) startValue = instr.GetUseValueList().get(0);
            // An empty unit-step loop only selects max(initial,bound); retain its guard.
            if (!headerEscapes && step == 1 && written.size() == 1 && condition == CompareInstr.CompareOp.SLT &&
                resolve(bound, expressions, pre, flow) != null && !expressions.containsKey(bound)) {
                rewriteEmpty(function, body, exit, index, bound);
                return true;
            }
            Form boundForm = resolve(bound, expressions, pre, flow);
            if (!isNumber(bound) || !isNumber(startValue)) {
                boolean forward = step == 1 && (condition == CompareInstr.CompareOp.SLT || condition == CompareInstr.CompareOp.NE);
                boolean backward = step == -1 && (condition == CompareInstr.CompareOp.SGT || condition == CompareInstr.CompareOp.NE);
                boolean inclusive = (step == 1 && condition == CompareInstr.CompareOp.SLE) ||
                    (step == -1 && condition == CompareInstr.CompareOp.SGE);
                if ((forward || backward) && boundForm != null &&
                    Collections.disjoint(boundForm.terms.keySet(), written) && independentReductions(state, written, index)) {
                    summarizeDynamic(function, body, headerEscapes ? header : exit, index, indexLoad, bound, step, 0, state, written, slots);
                    return true;
                }
                if (inclusive && boundForm != null && Collections.disjoint(boundForm.terms.keySet(), written) &&
                    independentReductions(state, written, index)) {
                    versionInclusive(function, header, body, headerEscapes ? header : exit, branch,
                        index, indexLoad, bound, step, state, written, slots);
                    return true;
                }
            }
            if (headerEscapes || !isNumber(startValue) || !isNumber(bound) || step == 0) continue;
            long start = number(startValue), end = number(bound), count;
            switch (condition) {
                case SLT -> { if (step <= 0) continue; count = end <= start ? 0 : (end - start + step - 1) / step; }
                case SLE -> { if (step <= 0) continue; count = end < start ? 0 : (end - start) / step + 1; }
                case SGT -> { if (step >= 0) continue; count = end >= start ? 0 : (start - end - step - 1) / -(long) step; }
                case SGE -> { if (step >= 0) continue; count = end > start ? 0 : (start - end) / -(long) step + 1; }
                case NE -> { if (step != 1 && step != -1) continue; count = Integer.toUnsignedLong((int)(step > 0 ? end - start : start - end)); }
                default -> { continue; }
            }
            long finalIndex = start + count * step;
            if (condition != CompareInstr.CompareOp.NE && (finalIndex < Integer.MIN_VALUE || finalIndex > Integer.MAX_VALUE)) continue;
            List<IrValue> basis = new ArrayList<>(state.keySet());
            for (Form f : state.values()) for (IrValue term : f.terms.keySet()) if (!basis.contains(term)) basis.add(term);
            if (basis.size() > 24) continue;
            int size = basis.size() + 1;
            int[][] matrix = identity(size);
            for (var entry : state.entrySet()) {
                int row = basis.indexOf(entry.getKey());
                Arrays.fill(matrix[row], 0);
                for (var term : entry.getValue().terms.entrySet()) matrix[row][basis.indexOf(term.getKey())] = term.getValue();
                matrix[row][size - 1] = entry.getValue().constant;
            }
            summarize(function, pre, exit, basis, written, slots, power(matrix, count));
            return true;
        }
        return false;
    }

    private static Form resolve(IrValue value, Map<IrValue, Form> expressions, IrBasicBlock pre, ControlFlow flow) {
        if (isNumber(value)) return new Form(number(value));
        if (expressions.containsKey(value)) return expressions.get(value);
        if (!value.GetIrType().IsInt32Type()) return null;
        if (value instanceof Instr instr && !flow.dominators.get(pre).contains(instr.GetInBasicBlock())) return null;
        return Form.variable(value);
    }

    private static void rewriteEmpty(IrFunction function, IrBasicBlock body, IrBasicBlock exit, IrValue slot, IrValue bound) {
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBuilder.SetCurrentFunction(function);
        IrBuilder.SetCurrentBasicBlock(body);
        try {
            body.GetInstrList().forEach(IrSupport::detach);
            body.GetInstrList().clear();
            new StoreInstr(bound, slot);
            new JumpInstr(exit);
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }

    private static boolean independentReductions(Map<IrValue, Form> state, Set<IrValue> written, IrValue index) {
        for (IrValue target : written) {
            if (target == index) continue;
            Form form = state.get(target);
            int self = form.terms.getOrDefault(target, 0);
            if (self != 0 && self != 1) return false;
            for (IrValue term : form.terms.keySet())
                if (written.contains(term) && term != target && term != index) return false;
        }
        return true;
    }

    private static IrValue literal(int n) { return constant(n, IrBaseType.INT32); }
    private static IrValue times(IrValue value, int n) { return n == 1 ? value : new AluInstr("*", value, literal(n)); }

    private static void versionInclusive(IrFunction function, IrBasicBlock header, IrBasicBlock original,
                                          IrBasicBlock exit, BranchInstr branch, IrValue index, IrValue start,
                                          IrValue bound, int step, Map<IrValue,Form> state,
                                          Set<IrValue> written, Set<IrValue> slots) {
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBasicBlock dispatch = new IrBasicBlock(IrBuilder.GetBasicBlockName(), function);
        IrBasicBlock fast = new IrBasicBlock(IrBuilder.GetBasicBlockName(), function);
        IrBuilder.SetCurrentFunction(function);
        IrBuilder.SetCurrentBasicBlock(dispatch);
        try {
            // At the extreme bound the original signed inclusive loop may wrap forever.
            CompareInstr safe = new CompareInstr("!=", bound, literal(step > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE));
            new BranchInstr(safe, fast, original);
            int operand = branch.GetTrueBlock() == original ? 1 : 2;
            original.DeleteUser(branch);
            branch.GetUseValueList().set(operand, dispatch);
            dispatch.AddUse(new midend.llvm.use.IrUse(branch, dispatch));
            function.GetBasicBlocks().add(function.GetBasicBlocks().indexOf(header) + 1, dispatch);
            function.GetBasicBlocks().add(function.GetBasicBlocks().indexOf(dispatch) + 1, fast);
            summarizeDynamic(function, fast, exit, index, start, bound, step, step, state, written, slots);
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }

    /** Retain the original zero-trip guard; all formulas execute only for a positive trip count. */
    private static void summarizeDynamic(IrFunction function, IrBasicBlock body, IrBasicBlock exit,
                                          IrValue index, IrValue start, IrValue bound, int step,
                                          int exclusiveAdjustment,
                                          Map<IrValue, Form> state, Set<IrValue> written, Set<IrValue> slots) {
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBuilder.SetCurrentFunction(function);
        IrBuilder.SetCurrentBasicBlock(body);
        try {
            body.GetInstrList().forEach(IrSupport::detach);
            body.GetInstrList().clear();
            if (exclusiveAdjustment != 0) bound = new AluInstr("+", bound, literal(exclusiveAdjustment));
            // Under the guard this is an unsigned count in [1, 2^32-1].
            IrValue count = new AluInstr("-", step > 0 ? bound : start, step > 0 ? start : bound);
            IrValue sumIndex = null;
            Map<IrValue, IrValue> initial = new HashMap<>();
            Map<IrValue, IrValue> results = new LinkedHashMap<>();
            for (IrValue target : written) {
                if (target == index) { results.put(target, bound); continue; }
                Form form = state.get(target);
                int self = form.terms.getOrDefault(target, 0);
                IrValue invariant = literal(form.constant);
                for (var term : form.terms.entrySet()) {
                    IrValue key = term.getKey();
                    if (key == index || key == target) continue;
                    IrValue value = initial.computeIfAbsent(key, k -> slots.contains(k) ? new LoadInstr(k) : k);
                    invariant = new AluInstr("+", invariant, times(value, term.getValue()));
                }
                IrValue result;
                int coefficient = form.terms.getOrDefault(index, 0);
                if (self == 1) {
                    result = new AluInstr("+", new LoadInstr(target), new AluInstr("*", count, invariant));
                    if (coefficient != 0) {
                        if (sumIndex == null) {
                            // Divide an even factor BEFORE multiplying, preserving the low 32 result bits.
                            IrValue half = new AluInstr("udiv", count, literal(2));
                            IrValue parity = new AluInstr("urem", count, literal(2));
                            IrValue factor = new AluInstr("+", new AluInstr("-", count, literal(1)), parity);
                            IrValue triangle = new AluInstr("*", half, factor);
                            sumIndex = new AluInstr(step > 0 ? "+" : "-", new AluInstr("*", count, start), triangle);
                        }
                        result = new AluInstr("+", result, times(sumIndex, coefficient));
                    }
                } else {
                    result = invariant;
                    if (coefficient != 0) result = new AluInstr("+", result,
                        times(new AluInstr("-", bound, literal(step)), coefficient));
                }
                results.put(target, result);
            }
            for (var result : results.entrySet()) new StoreInstr(result.getValue(), result.getKey());
            new JumpInstr(exit);
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }

    private static void summarize(IrFunction function, IrBasicBlock pre, IrBasicBlock exit,
                                  List<IrValue> basis, Set<IrValue> written, Set<IrValue> slots, int[][] matrix) {
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBuilder.SetCurrentFunction(function);
        IrBuilder.SetCurrentBasicBlock(pre);
        try {
            detach(pre.GetInstrList().remove(pre.GetInstrList().size() - 1));
            Map<IrValue, IrValue> initial = new HashMap<>();
            Map<IrValue, IrValue> results = new LinkedHashMap<>();
            // Load all inputs before any store: matrix rows describe simultaneous final values.
            for (IrValue target : written) {
                int[] row = matrix[basis.indexOf(target)];
                IrValue sum = constant(row[basis.size()], IrBaseType.INT32);
                for (int j = 0; j < basis.size(); j++) {
                    if (row[j] == 0) continue;
                    IrValue key = basis.get(j);
                    IrValue term = initial.computeIfAbsent(key, k -> slots.contains(k) ? new LoadInstr(k) : k);
                    if (row[j] != 1) term = new AluInstr("*", term, constant(row[j], IrBaseType.INT32));
                    sum = new AluInstr("+", sum, term);
                }
                results.put(target, sum);
            }
            for (var entry : results.entrySet()) new StoreInstr(entry.getValue(), entry.getKey());
            new JumpInstr(exit);
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }

    private static int[][] identity(int size) {
        int[][] matrix = new int[size][size];
        for (int i = 0; i < size; i++) matrix[i][i] = 1;
        return matrix;
    }
    private static int[][] multiply(int[][] a, int[][] b) {
        int n = a.length;
        int[][] result = new int[n][n];
        for (int i = 0; i < n; i++) for (int k = 0; k < n; k++) if (a[i][k] != 0)
            for (int j = 0; j < n; j++) result[i][j] += a[i][k] * b[k][j];
        return result;
    }
    private static int[][] power(int[][] matrix, long count) {
        int[][] result = identity(matrix.length);
        for (; count > 0; count >>= 1) {
            if ((count & 1) != 0) result = multiply(result, matrix);
            matrix = multiply(matrix, matrix);
        }
        return result;
    }
    private static final class Form {
        final Map<IrValue, Integer> terms = new LinkedHashMap<>();
        int constant;
        Form(int constant) { this.constant = constant; }
        static Form variable(IrValue value) { Form f = new Form(0); f.terms.put(value, 1); return f; }
        boolean isConstant() { return terms.isEmpty(); }
        Form plus(Form other, int factor) {
            Form f = new Form(constant + factor * other.constant);
            f.terms.putAll(terms);
            for (var t : other.terms.entrySet()) f.terms.merge(t.getKey(), factor * t.getValue(), Integer::sum);
            f.terms.values().removeIf(v -> v == 0);
            return f;
        }
        Form scale(int factor) { return new Form(0).plus(this, factor); }
    }
}
