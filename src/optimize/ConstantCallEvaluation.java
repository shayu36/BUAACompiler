package optimize;

import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;
import static optimize.IrSupport.*;

/** Bounded interpretation of pure scalar calls. Budget exhaustion retains the runtime call. */
public final class ConstantCallEvaluation {
    private record Invocation(IrFunction function, List<Integer> arguments) { }
    private final Map<Invocation, Integer> memo = new HashMap<>();
    private final Set<Invocation> active = new HashSet<>();
    private final FunctionEffects effects;
    private int remaining = 250_000;

    public ConstantCallEvaluation(IrModule module) {
        effects = new FunctionEffects(module);
    }

    public void run(IrModule module) {
        for (IrFunction function : module.GetFunctions()) {
            ControlFlow flow = new ControlFlow(function);
            flow.computeDominators();
            Map<List<IrValue>, List<CallInstr>> available = new HashMap<>();
            for (IrBasicBlock block : function.GetBasicBlocks()) {
                for (Iterator<Instr> iterator = block.GetInstrList().iterator(); iterator.hasNext();) {
                    Instr instr = iterator.next();
                    if (!(instr instanceof CallInstr call) || !effects.isPure(call.GetTargetFunction())) continue;
                    IrValue replacement = null;
                    if (call.GetParamList().stream().allMatch(IrSupport::isNumber)) {
                        List<Integer> args = call.GetParamList().stream().map(IrSupport::number).toList();
                        Integer result = evaluate(new Invocation(call.GetTargetFunction(), args), 0);
                        if (result != null) replacement = constant(result, call.GetIrType());
                    }
                    List<IrValue> key = List.copyOf(call.GetUseValueList());
                    List<CallInstr> candidates = available.computeIfAbsent(key, ignored -> new ArrayList<>());
                    if (replacement == null) {
                        for (CallInstr previous : candidates) {
                            if (flow.dominators.get(block).contains(previous.GetInBasicBlock())) {
                                replacement = previous;
                                break;
                            }
                        }
                    }
                    if (replacement != null) {
                        call.ModifyAllUsersToNewValue(replacement);
                        detach(call);
                        iterator.remove();
                    } else candidates.add(call);
                }
            }
        }
    }

    private Integer evaluate(Invocation invocation, int depth) {
        if (memo.containsKey(invocation)) return memo.get(invocation);
        if (remaining <= 0 || depth >= 96 || memo.size() >= 8192 || !active.add(invocation)) return null;
        try {
            Integer result = interpret(invocation, depth);
            if (result != null) memo.put(invocation, result);
            return result;
        } finally {
            active.remove(invocation);
        }
    }

    private Integer interpret(Invocation invocation, int depth) {
        Map<IrValue, Integer> values = new HashMap<>();
        for (int i = 0; i < invocation.arguments.size(); i++) {
            values.put(invocation.function.GetParameterList().get(i), invocation.arguments.get(i));
        }
        IrBasicBlock block = invocation.function.GetBasicBlocks().get(0);
        while (remaining > 0) {
            IrBasicBlock next = null;
            for (Instr instr : block.GetInstrList()) {
                if (--remaining < 0) return null;
                List<IrValue> operands = instr.GetUseValueList();
                if (instr instanceof ReturnInstr ret) return value(ret.GetReturnValue(), values);
                if (instr instanceof JumpInstr jump) { next = jump.GetTargetBlock(); break; }
                if (instr instanceof BranchInstr branch) {
                    Integer condition = value(branch.GetCond(), values);
                    if (condition == null) return null;
                    next = condition != 0 ? branch.GetTrueBlock() : branch.GetFalseBlock();
                    break;
                }
                if (instr instanceof AllocateInstr) continue;
                if (instr instanceof StoreInstr) {
                    Integer stored = value(operands.get(0), values);
                    if (stored == null) return null;
                    values.put(operands.get(1), stored);
                    continue;
                }
                Integer result;
                if (instr instanceof LoadInstr) result = values.get(operands.get(0));
                else if (instr instanceof CallInstr call) {
                    List<Integer> args = new ArrayList<>();
                    for (IrValue argument : call.GetParamList()) {
                        Integer number = value(argument, values);
                        if (number == null) return null;
                        args.add(number);
                    }
                    result = evaluate(new Invocation(call.GetTargetFunction(), List.copyOf(args)), depth + 1);
                } else {
                    List<Integer> args = new ArrayList<>();
                    for (IrValue argument : operands) {
                        Integer number = value(argument, values);
                        if (number == null) return null;
                        args.add(number);
                    }
                    result = evaluateInstruction(instr, args);
                }
                if (result == null) return null;
                values.put(instr, number(constant(result, instr.GetIrType())));
            }
            if (next == null) return null;
            block = next;
        }
        return null;
    }

    private Integer value(IrValue value, Map<IrValue, Integer> values) {
        return isNumber(value) ? number(value) : values.get(value);
    }

    private Integer evaluateInstruction(Instr instr, List<Integer> args) {
        if (instr instanceof ExtendInstr || instr instanceof TruncInstr) return args.get(0);
        int a = args.get(0), b = args.get(1);
        int signedA = signedNumber(constant(a, instr.GetUseValueList().get(0).GetIrType()));
        int signedB = signedNumber(constant(b, instr.GetUseValueList().get(1).GetIrType()));
        if (instr instanceof AluInstr alu) {
            return switch (alu.GetOpName()) {
                case "add" -> a + b; case "sub" -> a - b; case "mul" -> a * b;
                case "sdiv" -> signedB == 0 ? null : signedA / signedB;
                case "srem" -> signedB == 0 ? null : signedA % signedB;
                case "udiv" -> b == 0 ? null : Integer.divideUnsigned(a, b);
                case "urem" -> b == 0 ? null : Integer.remainderUnsigned(a, b);
                default -> null;
            };
        }
        if (instr instanceof CompareInstr compare) {
            int unsigned = Integer.compareUnsigned(a, b);
            boolean result = switch (compare.GetCompareOp()) {
                case EQ -> a == b; case NE -> a != b;
                case SLT -> signedA < signedB; case SLE -> signedA <= signedB;
                case SGT -> signedA > signedB; case SGE -> signedA >= signedB;
                case ULT -> unsigned < 0; case ULE -> unsigned <= 0;
                case UGT -> unsigned > 0; case UGE -> unsigned >= 0;
            };
            return result ? 1 : 0;
        }
        return null;
    }
}
