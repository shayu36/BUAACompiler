package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** Reuse the frame only when all self calls are direct tail calls and no local address escapes. */
public final class TailRecursion {
    private TailRecursion() { }
    public static void run(IrFunction function) {
        if (function.GetBasicBlocks().isEmpty() || function.IsMainFunction()) return;
        List<CallInstr> tails = new ArrayList<>();
        Set<IrValue> slots = IrSupport.scalarSlots(function);
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            List<Instr> list = block.GetInstrList();
            for (int i = 0; i < list.size(); i++) {
                Instr instr = list.get(i);
                // Reusing a local array would change the lifetime of pointers into older frames.
                if (instr instanceof AllocateInstr && !slots.contains(instr)) return;
                if (!(instr instanceof CallInstr call) || call.GetTargetFunction() != function) continue;
                if (i != list.size() - 2 || !(list.get(i + 1) instanceof ReturnInstr ret) ||
                    (ret.GetReturnValue() != call && !(ret.GetReturnValue() == null && call.GetIrType().IsVoidType()))) return;
                tails.add(call);
            }
        }
        if (tails.isEmpty()) return;
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBasicBlock header = function.GetBasicBlocks().get(0);
        IrBasicBlock entry = new IrBasicBlock(IrBuilder.GetBasicBlockName(), function);
        List<AllocateInstr> parameters = new ArrayList<>();
        IrBuilder.SetCurrentFunction(function);
        try {
            for (int i = 0; i < function.GetParameterList().size(); i++) {
                IrValue parameter = function.GetParameterList().get(i);
                IrBuilder.SetCurrentBasicBlock(entry);
                AllocateInstr slot = new AllocateInstr(parameter.GetIrType());
                parameters.add(slot);
                IrBuilder.SetCurrentBasicBlock(header);
                LoadInstr current = new LoadInstr(slot);
                header.GetInstrList().remove(current);
                header.GetInstrList().add(i, current);
                parameter.ModifyAllUsersToNewValue(current);
                IrBuilder.SetCurrentBasicBlock(entry);
                new StoreInstr(parameter, slot);
            }
            IrBuilder.SetCurrentBasicBlock(entry);
            new JumpInstr(header);
            function.GetBasicBlocks().add(0, entry);
            for (CallInstr call : tails) {
                IrBasicBlock block = call.GetInBasicBlock();
                List<Instr> list = block.GetInstrList();
                List<IrValue> arguments = List.copyOf(call.GetParamList());
                IrSupport.detach(list.remove(list.size() - 1));
                IrSupport.detach(list.remove(list.size() - 1));
                IrBuilder.SetCurrentBasicBlock(block);
                // Arguments are already SSA values: all expressions precede these assignments.
                for (int i = 0; i < arguments.size(); i++) new StoreInstr(arguments.get(i), parameters.get(i));
                new JumpInstr(header);
            }
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }
}
