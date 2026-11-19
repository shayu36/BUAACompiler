package optimize;

import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;
import static optimize.IrSupport.*;

/** Fold scalar globals only when every possible write preserves their initializer. */
public final class GlobalConstants {
    private GlobalConstants() { }

    public static void run(IrModule module) {
        Map<IrValue, IrValue> known = new HashMap<>();
        for (IrGlobalValue global : module.GetGlobalValues()) {
            if (isNumber(global.GetInitializer())) known.put(global, global.GetInitializer());
        }
        for (IrFunction function : module.GetFunctions()) {
            for (IrBasicBlock block : function.GetBasicBlocks()) {
                for (Instr instr : block.GetInstrList()) {
                    List<IrValue> args = instr.GetUseValueList();
                    for (int i = 0; i < args.size(); i++) {
                        IrValue argument = args.get(i);
                        if (!known.containsKey(argument)) continue;
                        if (instr instanceof LoadInstr && i == 0) continue;
                        if (instr instanceof StoreInstr && i == 1 && isNumber(args.get(0)) &&
                            number(args.get(0)) == number(known.get(argument))) continue;
                        known.remove(argument);
                    }
                }
            }
        }
        for (IrFunction function : module.GetFunctions()) {
            for (IrBasicBlock block : function.GetBasicBlocks()) {
                for (Iterator<Instr> iterator = block.GetInstrList().iterator(); iterator.hasNext();) {
                    Instr instr = iterator.next();
                    if (instr instanceof LoadInstr && known.containsKey(instr.GetUseValueList().get(0))) {
                        instr.ModifyAllUsersToNewValue(known.get(instr.GetUseValueList().get(0)));
                        detach(instr);
                        iterator.remove();
                    }
                }
            }
        }
    }
}
