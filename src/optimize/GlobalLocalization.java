package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** A scalar observed only by the unique main invocation needs no global memory home. */
public final class GlobalLocalization {
    private GlobalLocalization() { }
    public static void run(IrModule module) {
        IrFunction main = module.GetMainFunction();
        Set<IrGlobalValue> candidates = new LinkedHashSet<>();
        for (IrGlobalValue global : module.GetGlobalValues()) {
            if (IrSupport.isNumber(global.GetInitializer())) candidates.add(global);
        }
        Set<IrGlobalValue> used = new HashSet<>();
        for (IrFunction function : module.GetFunctions()) for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr instanceof CallInstr call && call.GetTargetFunction() == main) return;
                List<IrValue> args = instr.GetUseValueList();
                for (int i = 0; i < args.size(); i++) if (args.get(i) instanceof IrGlobalValue global) {
                    used.add(global);
                    if (function != main || !((instr instanceof LoadInstr && i == 0) ||
                        (instr instanceof StoreInstr && i == 1))) candidates.remove(global);
                }
            }
        }
        candidates.retainAll(used);
        if (candidates.isEmpty()) return;
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBasicBlock temporary = new IrBasicBlock(IrBuilder.GetBasicBlockName(), main);
        IrBuilder.SetCurrentFunction(main);
        IrBuilder.SetCurrentBasicBlock(temporary);
        try {
            for (IrGlobalValue global : candidates) {
                AllocateInstr slot = new AllocateInstr(global.GetInitializer().GetIrType());
                new StoreInstr(global.GetInitializer(), slot);
                global.ModifyAllUsersToNewValue(slot);
            }
            IrBasicBlock entry = main.GetBasicBlocks().get(0);
            for (Instr instr : temporary.GetInstrList()) instr.SetInBasicBlock(entry);
            entry.GetInstrList().addAll(0, temporary.GetInstrList());
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }
}
