package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.use.IrUse;
import midend.llvm.value.*;
import java.util.*;

/** A leaf function can retain private copies of nonescaping global scalars until it returns. */
public final class ScalarGlobalCache {
    private ScalarGlobalCache() { }
    public static void run(IrModule module) {
        Set<IrGlobalValue> globals = new LinkedHashSet<>();
        for (IrGlobalValue g : module.GetGlobalValues()) if (IrSupport.isNumber(g.GetInitializer())) globals.add(g);
        for (IrFunction f : module.GetFunctions()) for (IrBasicBlock b : f.GetBasicBlocks()) for (Instr instr : b.GetInstrList()) {
            List<IrValue> values = instr.GetUseValueList();
            for (int i = 0; i < values.size(); i++) if (values.get(i) instanceof IrGlobalValue g &&
                !((instr instanceof LoadInstr && i == 0) || (instr instanceof StoreInstr && i == 1))) globals.remove(g);
        }
        for (IrFunction f : module.GetFunctions()) {
            Map<IrGlobalValue,List<Instr>> uses = new LinkedHashMap<>();
            Set<IrGlobalValue> writes = new HashSet<>();
            List<ReturnInstr> returns = new ArrayList<>();
            boolean leaf = true;
            for (IrBasicBlock b : f.GetBasicBlocks()) for (Instr instr : b.GetInstrList()) {
                if (IrSupport.isCall(instr)) leaf = false;
                if (instr instanceof ReturnInstr ret) returns.add(ret);
                IrValue pointer = instr instanceof LoadInstr ? instr.GetUseValueList().get(0) :
                    instr instanceof StoreInstr ? instr.GetUseValueList().get(1) : null;
                if (pointer instanceof IrGlobalValue g && globals.contains(g)) {
                    uses.computeIfAbsent(g, ignored -> new ArrayList<>()).add(instr);
                    if (instr instanceof StoreInstr) writes.add(g);
                }
            }
            if (!leaf || uses.isEmpty()) continue;
            IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
            IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
            IrBasicBlock temporary = new IrBasicBlock(IrBuilder.GetBasicBlockName(), f);
            IrBuilder.SetCurrentFunction(f);
            IrBuilder.SetCurrentBasicBlock(temporary);
            try {
                for (var entry : uses.entrySet()) {
                    if (entry.getValue().size() < 2) continue;
                    IrGlobalValue global = entry.getKey();
                    AllocateInstr slot = new AllocateInstr(global.GetInitializer().GetIrType());
                    new StoreInstr(new LoadInstr(global), slot);
                    for (Instr instr : entry.getValue()) {
                        int position = instr instanceof LoadInstr ? 0 : 1;
                        instr.GetUseValueList().set(position, slot);
                        global.DeleteUser(instr);
                        slot.AddUse(new IrUse(instr, slot));
                    }
                    if (writes.contains(global)) for (ReturnInstr ret : returns) {
                        IrBasicBlock block = ret.GetInBasicBlock();
                        IrBasicBlock epilogue = new IrBasicBlock(IrBuilder.GetBasicBlockName(), f);
                        IrBuilder.SetCurrentBasicBlock(epilogue);
                        new StoreInstr(new LoadInstr(slot), global);
                        for (Instr instr : epilogue.GetInstrList()) instr.SetInBasicBlock(block);
                        block.GetInstrList().addAll(block.GetInstrList().indexOf(ret), epilogue.GetInstrList());
                        IrBuilder.SetCurrentBasicBlock(temporary);
                    }
                }
                IrBasicBlock entry = f.GetBasicBlocks().get(0);
                for (Instr instr : temporary.GetInstrList()) instr.SetInBasicBlock(entry);
                entry.GetInstrList().addAll(0, temporary.GetInstrList());
            } finally {
                IrBuilder.SetCurrentFunction(savedFunction);
                IrBuilder.SetCurrentBasicBlock(savedBlock);
            }
        }
    }
}
