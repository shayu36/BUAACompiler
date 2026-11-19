package optimize;

import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;
import static optimize.IrSupport.*;

/** Ordered passes, repeated because folding can expose unreachable and dead code. */
public final class OptimizeManager {
    private OptimizeManager() { }

    public static void optimize(IrModule module) {
        removeUnusedFunctions(module);
        for (IrFunction function : module.GetFunctions()) TailRecursion.run(function);
        ScalarGlobalCache.run(module);
        for (int iteration = 0; iteration < 3; iteration++) {
            GlobalLocalization.run(module);
            simplifyFunctions(module);
            for (IrFunction function : module.GetFunctions()) AffineLoops.run(function);
            simplifyFunctions(module);
            GlobalConstants.run(module);
            SmallFunctionInline.run(module);
            ControlFlowInline.run(module);
            new ConstantCallEvaluation(module).run(module);
            removeUnusedFunctions(module);
        }
        simplifyFunctions(module);
        FunctionEffects effects = new FunctionEffects(module);
        for (IrFunction function : module.GetFunctions()) {
            hoistInvariants(function, effects);
            LoopInvariantCalls.run(function, effects);
        }
        simplifyFunctions(module);
        for (int round = 0; round < 8; round++) {
            boolean changed = false;
            for (IrFunction function : module.GetFunctions()) changed |= AffineLoops.run(function);
            if (!changed) break;
            simplifyFunctions(module);
        }
        removeUnusedFunctions(module);
    }

    private static void simplifyFunctions(IrModule module) {
        for (IrFunction function : module.GetFunctions()) {
            for (int round = 0; round < 8; round++) {
                String before = function.toString();
                simplifyControlFlow(function);
                propagateMemory(function);
                foldAndNumber(function);
                MemoryForwarding.run(function);
                DivisionRemainders.run(function);
                removeDead(function);
                if (before.equals(function.toString())) break;
            }
        }
    }

    private static void removeUnusedFunctions(IrModule module) {
        Set<IrFunction> reachable = new HashSet<>();
        Deque<IrFunction> queue = new ArrayDeque<>();
        queue.add(module.GetMainFunction());
        while (!queue.isEmpty()) {
            IrFunction function = queue.remove();
            if (!reachable.add(function)) continue;
            for (IrBasicBlock block : function.GetBasicBlocks()) {
                for (Instr instr : block.GetInstrList()) {
                    if (instr instanceof CallInstr call) queue.add(call.GetTargetFunction());
                }
            }
        }
        module.GetFunctions().removeIf(function -> !reachable.contains(function));
    }

    private static void simplifyControlFlow(IrFunction function) {
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            List<Instr> instructions = block.GetInstrList();
            boolean terminated = false;
            for (ListIterator<Instr> it = instructions.listIterator(); it.hasNext();) {
                Instr instr = it.next();
                if (terminated) { detach(instr); it.remove(); continue; }
                if (instr instanceof BranchInstr branch &&
                    (isNumber(branch.GetCond()) || branch.GetTrueBlock() == branch.GetFalseBlock())) {
                    IrBasicBlock target = branch.GetTrueBlock();
                    if (isNumber(branch.GetCond()) && number(branch.GetCond()) == 0) target = branch.GetFalseBlock();
                    detach(instr);
                    instr = new JumpInstr(target, block);
                    it.set(instr);
                }
                terminated = instr instanceof JumpInstr || instr instanceof BranchInstr || instr instanceof ReturnInstr;
            }
        }
        ControlFlow flow = new ControlFlow(function);
        Set<IrBasicBlock> reachable = new HashSet<>();
        Deque<IrBasicBlock> queue = new ArrayDeque<>();
        queue.add(flow.blocks.get(0));
        while (!queue.isEmpty()) {
            IrBasicBlock block = queue.remove();
            if (reachable.add(block)) queue.addAll(flow.successors.get(block));
        }
        for (IrBasicBlock block : new ArrayList<>(flow.blocks)) {
            if (!reachable.contains(block)) {
                block.GetInstrList().forEach(IrSupport::detach);
                function.GetBasicBlocks().remove(block);
            }
        }
        // One predecessor and one outgoing edge need no intervening basic block.
        for (int round = 0; round < 100; round++) {
            flow = new ControlFlow(function);
            boolean merged = false;
            for (IrBasicBlock block : new ArrayList<>(flow.blocks)) {
                if (block == flow.blocks.get(0) || flow.predecessors.get(block).size() != 1) continue;
                IrBasicBlock pred = flow.predecessors.get(block).iterator().next();
                if (pred == block || flow.successors.get(pred).size() != 1) continue;
                List<Instr> list = pred.GetInstrList();
                if (!(list.get(list.size() - 1) instanceof JumpInstr)) continue;
                detach(list.remove(list.size() - 1));
                for (Instr instr : block.GetInstrList()) pred.AddInstr(instr);
                function.GetBasicBlocks().remove(block);
                merged = true;
                break;
            }
            if (!merged) break;
        }
    }

    private static Map<IrValue, IrValue> incoming(ControlFlow flow, IrBasicBlock block,
            Map<IrBasicBlock, Map<IrValue, IrValue>> outgoing) {
        Map<IrValue, IrValue> result = new LinkedHashMap<>();
        if (block == flow.blocks.get(0)) return result;
        boolean first = true;
        for (IrBasicBlock pred : flow.predecessors.get(block)) {
            Map<IrValue, IrValue> values = outgoing.get(pred);
            if (first) { result.putAll(values); first = false; }
            else result.entrySet().removeIf(entry -> !same(entry.getValue(), values.get(entry.getKey())));
        }
        return result;
    }

    private static boolean same(IrValue left, IrValue right) {
        return left == right || (isNumber(left) && isNumber(right) &&
            left.GetIrType().equals(right.GetIrType()) && number(left) == number(right));
    }

    private static void propagateMemory(IrFunction function) {
        ControlFlow flow = new ControlFlow(function);
        Set<IrValue> slots = scalarSlots(function);
        Map<IrBasicBlock, Map<IrValue, IrValue>> outgoing = new LinkedHashMap<>();
        for (IrBasicBlock block : flow.blocks) outgoing.put(block, new LinkedHashMap<>());
        boolean changed;
        do {
            changed = false;
            for (IrBasicBlock block : flow.blocks) {
                Map<IrValue, IrValue> values = incoming(flow, block, outgoing);
                for (Instr instr : block.GetInstrList()) {
                    List<IrValue> args = instr.GetUseValueList();
                    if (instr instanceof StoreInstr && slots.contains(args.get(1))) values.put(args.get(1), args.get(0));
                }
                if (!values.equals(outgoing.get(block))) { outgoing.put(block, values); changed = true; }
            }
        } while (changed);
        // Solve reaching stores before mutating operands, so joins and backedges are respected.
        Map<Instr, IrValue> replacements = new LinkedHashMap<>();
        for (IrBasicBlock block : flow.blocks) {
            Map<IrValue, IrValue> values = incoming(flow, block, outgoing);
            Map<IrValue, IrValue> loads = new HashMap<>();
            for (Instr instr : block.GetInstrList()) {
                List<IrValue> args = instr.GetUseValueList();
                if (instr instanceof StoreInstr) {
                    loads.clear();
                    if (slots.contains(args.get(1))) values.put(args.get(1), args.get(0));
                } else if (isCall(instr)) {
                    loads.clear();
                } else if (instr instanceof LoadInstr) {
                    IrValue pointer = args.get(0);
                    IrValue known = slots.contains(pointer) ? values.get(pointer) : loads.get(pointer);
                    if (known != null && known != instr) replacements.put(instr, known);
                    else loads.put(pointer, instr);
                }
            }
        }
        for (Map.Entry<Instr, IrValue> replacement : replacements.entrySet()) {
            Instr instr = replacement.getKey();
            IrValue value = replacement.getValue();
            Set<IrValue> visited = new HashSet<>();
            while (value instanceof Instr next && replacements.containsKey(next) && visited.add(value)) {
                value = replacements.get(next);
            }
            if (value == instr || visited.contains(value)) continue;
            instr.ModifyAllUsersToNewValue(value);
            detach(instr);
            instr.GetInBasicBlock().GetInstrList().remove(instr);
        }
    }

    private static void foldAndNumber(IrFunction function) {
        ControlFlow flow = new ControlFlow(function);
        flow.computeDominators();
        Map<String, List<Instr>> expressions = new HashMap<>();
        for (IrBasicBlock block : flow.blocks) {
            for (Iterator<Instr> it = block.GetInstrList().iterator(); it.hasNext();) {
                Instr instr = it.next();
                IrValue replacement = fold(instr);
                if (replacement == null && isPure(instr)) {
                    String key = expressionKey(instr);
                    List<Instr> candidates = expressions.computeIfAbsent(key, ignored -> new ArrayList<>());
                    for (Instr previous : candidates) {
                        if (flow.dominators.get(block).contains(previous.GetInBasicBlock())) {
                            replacement = previous;
                            break;
                        }
                    }
                    if (replacement == null) candidates.add(instr);
                }
                if (replacement != null) {
                    instr.ModifyAllUsersToNewValue(replacement);
                    detach(instr);
                    it.remove();
                }
            }
        }
    }

    private static String expressionKey(Instr instr) {
        List<String> operands = new ArrayList<>();
        for (IrValue value : instr.GetUseValueList()) operands.add(value.GetIrType() + " " + value.GetIrName());
        String operation = instr.GetInstrType().toString();
        if (instr instanceof AluInstr alu) {
            operation = alu.GetOpName();
            if (operation.equals("add") || operation.equals("mul")) Collections.sort(operands);
        }
        if (instr instanceof CompareInstr compare) {
            operation = compare.GetCompareOp().toString();
            if (operation.equals("EQ") || operation.equals("NE")) Collections.sort(operands);
        }
        return operation + "/" + instr.GetIrType() + "/" + String.join(",", operands);
    }

    private static IrValue fold(Instr instr) {
        List<IrValue> args = instr.GetUseValueList();
        if (instr instanceof CompareInstr compare && compare.GetCompareOp() == CompareInstr.CompareOp.NE) {
            for (int i = 0; i < 2; i++) {
                if (isNumber(args.get(1 - i)) && number(args.get(1 - i)) == 0) {
                    IrValue value = args.get(i);
                    while (value instanceof ExtendInstr) value = ((Instr) value).GetUseValueList().get(0);
                    if (value.GetIrType().IsInt1Type()) return value;
                }
            }
        }
        if ((instr instanceof TruncInstr || instr instanceof ExtendInstr) && isNumber(args.get(0))) {
            return constant(number(args.get(0)), instr.GetIrType());
        }
        if (instr instanceof AluInstr alu) {
            IrValue left = args.get(0), right = args.get(1);
            String op = alu.GetOpName();
            if (isNumber(left) && isNumber(right)) {
                int a = number(left), b = number(right);
                if (op.startsWith("s")) { a = signedNumber(left); b = signedNumber(right); }
                if ((op.endsWith("div") || op.endsWith("rem")) && b == 0) return null;
                int value = switch (op) {
                    case "add" -> a + b;
                    case "sub" -> a - b;
                    case "mul" -> a * b;
                    case "sdiv" -> a / b;
                    case "srem" -> a % b;
                    case "udiv" -> Integer.divideUnsigned(a, b);
                    case "urem" -> Integer.remainderUnsigned(a, b);
                    default -> throw new IllegalStateException(op);
                };
                return constant(value, instr.GetIrType());
            }
            if (isNumber(right)) {
                int n = number(right);
                if (n == 0 && (op.equals("add") || op.equals("sub"))) return left;
                if (n == 1 && (op.equals("mul") || op.endsWith("div"))) return left;
                if ((n == 0 && op.equals("mul")) || (n == 1 && op.endsWith("rem"))) return constant(0, instr.GetIrType());
            }
            if (isNumber(left) && op.equals("add") && number(left) == 0) return right;
            if (left == right && op.equals("sub")) return constant(0, instr.GetIrType());
            if (instr.GetIrType().IsInt32Type() && (op.equals("add") || op.equals("sub"))) {
                Map<IrValue, Integer> terms = new LinkedHashMap<>();
                int[] sumAndBudget = {0, 128};
                if (collectLinear(instr, 1, terms, sumAndBudget, 0)) {
                    terms.values().removeIf(coefficient -> coefficient == 0);
                    if (terms.isEmpty()) return constant(sumAndBudget[0], instr.GetIrType());
                    if (terms.size() == 1 && sumAndBudget[0] == 0) {
                        var term = terms.entrySet().iterator().next();
                        if (term.getValue() == 1) return term.getKey();
                    }
                }
            }
        }
        if (instr instanceof CompareInstr compare && isNumber(args.get(0)) && isNumber(args.get(1))) {
            int a = signedNumber(args.get(0)), b = signedNumber(args.get(1));
            int unsigned = Integer.compareUnsigned(number(args.get(0)), number(args.get(1)));
            boolean result = switch (compare.GetCompareOp()) {
                case EQ -> a == b; case NE -> a != b;
                case SGT -> a > b; case SGE -> a >= b; case SLT -> a < b; case SLE -> a <= b;
                case UGT -> unsigned > 0; case UGE -> unsigned >= 0; case ULT -> unsigned < 0; case ULE -> unsigned <= 0;
            };
            return constant(result ? 1 : 0, instr.GetIrType());
        }
        return null;
    }

    private static boolean collectLinear(IrValue value, int sign, Map<IrValue, Integer> terms,
                                         int[] sumAndBudget, int depth) {
        if (--sumAndBudget[1] < 0 || depth > 16) return false;
        if (isNumber(value)) sumAndBudget[0] += sign * number(value);
        else if (value instanceof AluInstr alu && alu.GetIrType().IsInt32Type() &&
            (alu.GetOpName().equals("add") || alu.GetOpName().equals("sub"))) {
            int rightSign = alu.GetOpName().equals("sub") ? -sign : sign;
            return collectLinear(alu.GetUseValueList().get(0), sign, terms, sumAndBudget, depth + 1) &&
                collectLinear(alu.GetUseValueList().get(1), rightSign, terms, sumAndBudget, depth + 1);
        } else terms.merge(value, sign, Integer::sum);
        return true;
    }

    private static void removeDead(IrFunction function) {
        LiveAnalysis live = new LiveAnalysis(function);
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            Set<IrValue> active = new HashSet<>(live.out.get(block));
            List<Instr> list = block.GetInstrList();
            for (int i = list.size() - 1; i >= 0; i--) {
                Instr instr = list.get(i);
                IrValue definition = live.definition(instr);
                if (instr instanceof StoreInstr && live.slots.contains(definition) && !active.contains(definition)) {
                    detach(instr); list.remove(i); continue;
                }
                active.remove(definition);
                active.addAll(live.uses(instr));
            }
        }
        Set<Instr> needed = new HashSet<>();
        Deque<Instr> queue = new ArrayDeque<>();
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (!instr.DefValue() || isCall(instr)) queue.add(instr);
            }
        }
        while (!queue.isEmpty()) {
            Instr instr = queue.remove();
            if (!needed.add(instr)) continue;
            for (IrValue value : instr.GetUseValueList()) if (value instanceof Instr dependency) queue.add(dependency);
        }
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            block.GetInstrList().removeIf(instr -> {
                if (needed.contains(instr)) return false;
                detach(instr);
                return true;
            });
        }
    }

    private static void hoistInvariants(IrFunction function, FunctionEffects effects) {
        ControlFlow flow = new ControlFlow(function);
        flow.computeDominators();
        // Group all backedges to each header before determining invariance.
        Map<IrBasicBlock, Set<IrBasicBlock>> loops = new LinkedHashMap<>();
        for (IrBasicBlock tail : flow.blocks) {
            for (IrBasicBlock header : flow.successors.get(tail)) {
                if (!flow.dominators.get(tail).contains(header)) continue;
                Set<IrBasicBlock> loop = loops.computeIfAbsent(header, ignored -> new LinkedHashSet<>());
                loop.add(header);
                Deque<IrBasicBlock> queue = new ArrayDeque<>();
                queue.add(tail);
                while (!queue.isEmpty()) {
                    IrBasicBlock block = queue.remove();
                    if (loop.add(block)) queue.addAll(flow.predecessors.get(block));
                }
            }
        }
        for (var entry : loops.entrySet()) {
            Set<IrBasicBlock> loop = entry.getValue();
            Set<IrValue> slots = scalarSlots(function);
            List<IrBasicBlock> outside = flow.predecessors.get(entry.getKey()).stream().filter(b -> !loop.contains(b)).toList();
            if (outside.size() != 1) continue;
            IrBasicBlock preheader = outside.get(0);
            if (flow.successors.get(preheader).size() != 1) continue;
            boolean changed;
            do {
                changed = false;
                for (IrBasicBlock block : loop) {
                    for (Iterator<Instr> it = block.GetInstrList().iterator(); it.hasNext();) {
                        Instr instr = it.next();
                        boolean stableLoad = instr instanceof LoadInstr &&
                            stableMemory(instr.GetUseValueList().get(0), slots, loop, effects);
                        if ((!isPure(instr) && !stableLoad) || instr instanceof GepInstr) continue;
                        if (instr instanceof AluInstr alu && (alu.GetOpName().endsWith("div") || alu.GetOpName().endsWith("rem"))) {
                            IrValue divisor = alu.GetUseValueList().get(1);
                            if (!isNumber(divisor) || number(divisor) == 0) continue;
                        }
                        boolean invariant = true;
                        for (IrValue value : instr.GetUseValueList()) {
                            if (value instanceof Instr definition && (loop.contains(definition.GetInBasicBlock()) ||
                                !flow.dominators.get(preheader).contains(definition.GetInBasicBlock()))) invariant = false;
                        }
                        if (invariant) {
                            it.remove(); preheader.AddInstrBeforeTerminator(instr); changed = true;
                        }
                    }
                }
            } while (changed);
        }
    }

    private static boolean stableMemory(IrValue pointer, Set<IrValue> slots,
                                        Set<IrBasicBlock> loop, FunctionEffects effects) {
        boolean local = slots.contains(pointer);
        if (!local && !(pointer instanceof IrGlobalValue global && isNumber(global.GetInitializer()))) return false;
        for (IrBasicBlock block : loop) {
            for (Instr instr : block.GetInstrList()) {
                if (instr instanceof StoreInstr) {
                    IrValue target = instr.GetUseValueList().get(1);
                    while (target instanceof GepInstr) target = ((Instr) target).GetUseValueList().get(0);
                    if (target == pointer) return false;
                    if (!local && !(target instanceof IrGlobalValue) && !(target instanceof AllocateInstr)) return false;
                }
                if (!local && isCall(instr) &&
                    (!(instr instanceof CallInstr call) || !effects.isPure(call.GetTargetFunction()))) return false;
            }
        }
        return true;
    }
}
