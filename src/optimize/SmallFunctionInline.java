package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** Clone small straight-line callees, then let the ordinary passes simplify arguments. */
public final class SmallFunctionInline {
    private SmallFunctionInline() { }

    public static void run(IrModule module) {
        for (IrFunction caller : module.GetFunctions()) {
            int remaining = 300;
            for (IrBasicBlock block : new ArrayList<>(caller.GetBasicBlocks())) {
                for (Instr instr : new ArrayList<>(block.GetInstrList())) {
                    if (!(instr instanceof CallInstr call)) continue;
                    IrFunction callee = call.GetTargetFunction();
                    if (callee == caller || !eligible(callee)) continue;
                    int size = callee.GetBasicBlocks().get(0).GetInstrList().size();
                    if (size > remaining) continue;
                    inline(call, caller, block);
                    remaining -= size;
                }
            }
        }
    }

    private static boolean eligible(IrFunction function) {
        if (function.GetBasicBlocks().size() != 1) return false;
        List<Instr> instructions = function.GetBasicBlocks().get(0).GetInstrList();
        if (instructions.size() > 24 || instructions.isEmpty()) return false;
        for (Instr instr : instructions) {
            if (IrSupport.isCall(instr) || instr instanceof BranchInstr || instr instanceof JumpInstr) return false;
            if (instr instanceof AllocateInstr alloc && alloc.GetTargetType().IsArrayType()) return false;
        }
        return instructions.get(instructions.size() - 1) instanceof ReturnInstr;
    }

    private static void inline(CallInstr call, IrFunction caller, IrBasicBlock block) {
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBasicBlock temporary = new IrBasicBlock(IrBuilder.GetBasicBlockName(), caller);
        Map<IrValue, IrValue> values = new HashMap<>();
        IrFunction callee = call.GetTargetFunction();
        for (int i = 0; i < call.GetParamList().size(); i++) {
            values.put(callee.GetParameterList().get(i), call.GetParamList().get(i));
        }
        IrValue result = null;
        IrBuilder.SetCurrentFunction(caller);
        IrBuilder.SetCurrentBasicBlock(temporary);
        try {
            for (Instr original : callee.GetBasicBlocks().get(0).GetInstrList()) {
                if (original instanceof ReturnInstr ret) {
                    result = values.getOrDefault(ret.GetReturnValue(), ret.GetReturnValue());
                } else {
                    Instr copy = copy(original, values);
                    values.put(original, copy);
                }
            }
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
        if (result != null) call.ModifyAllUsersToNewValue(result);
        int index = block.GetInstrList().indexOf(call);
        block.GetInstrList().remove(index);
        IrSupport.detach(call);
        for (Instr copy : temporary.GetInstrList()) copy.SetInBasicBlock(block);
        block.GetInstrList().addAll(index, temporary.GetInstrList());
    }

    static Instr copy(Instr instr, Map<IrValue, IrValue> values) {
        List<IrValue> operands = instr.GetUseValueList().stream().map(v -> values.getOrDefault(v, v)).toList();
        return switch (instr.GetInstrType()) {
            case ALLOCATE -> new AllocateInstr(((AllocateInstr) instr).GetTargetType());
            case LOAD -> new LoadInstr(operands.get(0));
            case STORE -> new StoreInstr(operands.get(0), operands.get(1));
            case GEP -> new GepInstr(operands.get(0), operands.get(1));
            case EXTEND -> new ExtendInstr(operands.get(0), instr.GetIrType());
            case TRUNC -> new TruncInstr(operands.get(0), instr.GetIrType());
            case ALU -> new AluInstr(aluSymbol(((AluInstr) instr).GetOpName()), operands.get(0), operands.get(1));
            case CMP -> new CompareInstr(compareSymbol(((CompareInstr) instr).GetCompareOp()), operands.get(0), operands.get(1));
            default -> throw new IllegalArgumentException("Unsupported inline instruction " + instr.GetInstrType());
        };
    }

    private static String aluSymbol(String operation) {
        return switch (operation) {
            case "add" -> "+"; case "sub" -> "-"; case "mul" -> "*";
            case "sdiv" -> "/"; case "srem" -> "%"; default -> operation;
        };
    }

    private static String compareSymbol(CompareInstr.CompareOp operation) {
        return switch (operation) {
            case EQ -> "=="; case NE -> "!="; case SGT -> ">";
            case SGE -> ">="; case SLT -> "<"; case SLE -> "<=";
            default -> operation.toString().toLowerCase(Locale.ROOT);
        };
    }
}
