package optimize;

import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** Pure scalar functions may recurse, but cannot observe external memory or perform I/O. */
public final class FunctionEffects {
    private final Set<IrFunction> pure = new HashSet<>();

    public FunctionEffects(IrModule module) {
        for (IrFunction function : module.GetFunctions()) {
            if (locallyPure(function)) pure.add(function);
        }
        boolean changed;
        do {
            changed = false;
            for (IrFunction function : new ArrayList<>(pure)) {
                for (IrBasicBlock block : function.GetBasicBlocks()) {
                    for (Instr instr : block.GetInstrList()) {
                        if (instr instanceof CallInstr call && !pure.contains(call.GetTargetFunction())) {
                            changed |= pure.remove(function);
                        }
                    }
                }
            }
        } while (changed);
    }

    public boolean isPure(IrFunction function) {
        return pure.contains(function);
    }

    private boolean locallyPure(IrFunction function) {
        if (function.GetBasicBlocks().isEmpty() || function.GetReturnType().IsVoidType()) return false;
        for (IrParameter parameter : function.GetParameterList()) {
            if (parameter.GetIrType().IsPointerType()) return false;
        }
        Set<IrValue> slots = IrSupport.scalarSlots(function);
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr.GetInstrType() == InstrType.IO || instr instanceof GepInstr) return false;
                if (instr instanceof AllocateInstr && !slots.contains(instr)) return false;
                if (instr instanceof LoadInstr && !slots.contains(instr.GetUseValueList().get(0))) return false;
                if (instr instanceof StoreInstr && !slots.contains(instr.GetUseValueList().get(1))) return false;
            }
        }
        return true;
    }
}
