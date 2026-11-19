package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.IrModule;
import midend.llvm.instr.*;
import midend.llvm.use.IrUse;
import midend.llvm.value.*;
import java.util.*;

/** Bounded inlining of small leaf functions with branches and multiple returns. */
public final class ControlFlowInline {
    private ControlFlowInline() { }
    public static void run(IrModule module) {
        for (IrFunction caller : module.GetFunctions()) {
            int budget = 240;
            List<CallInstr> calls = new ArrayList<>();
            for (IrBasicBlock b : caller.GetBasicBlocks()) for (Instr i : b.GetInstrList())
                if (i instanceof CallInstr call) calls.add(call);
            for (CallInstr call : calls) {
                IrFunction callee = call.GetTargetFunction();
                int size = size(callee);
                if (callee == caller || size < 0 || size > budget) continue;
                inline(call, caller);
                budget -= size;
            }
        }
    }
    private static int size(IrFunction function) {
        if (function.GetBasicBlocks().size() < 2 || function.GetBasicBlocks().size() > 8) return -1;
        // Keep loop-containing calls available for invariant-call caching.
        ControlFlow flow = new ControlFlow(function);
        flow.computeDominators();
        for (IrBasicBlock block : flow.blocks) for (IrBasicBlock next : flow.successors.get(block))
            if (flow.dominators.get(block).contains(next)) return -1;
        int size = 0;
        for (IrBasicBlock block : function.GetBasicBlocks()) for (Instr instr : block.GetInstrList()) {
            if (++size > 60 || IrSupport.isCall(instr)) return -1;
            if (instr instanceof AllocateInstr alloc && alloc.GetTargetType().IsArrayType()) return -1;
        }
        return size;
    }
    private static void inline(CallInstr call, IrFunction caller) {
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBasicBlock origin = call.GetInBasicBlock();
        IrFunction callee = call.GetTargetFunction();
        Map<IrValue,IrValue> values = new HashMap<>();
        List<IrBasicBlock> clones = new ArrayList<>();
        for (IrBasicBlock block : callee.GetBasicBlocks()) {
            IrBasicBlock copy = new IrBasicBlock(IrBuilder.GetBasicBlockName(), caller);
            clones.add(copy);
            values.put(block, copy);
        }
        for (int i = 0; i < call.GetParamList().size(); i++) values.put(callee.GetParameterList().get(i), call.GetParamList().get(i));
        IrBasicBlock continuation = new IrBasicBlock(IrBuilder.GetBasicBlockName(), caller);
        IrBasicBlock temporary = new IrBasicBlock(IrBuilder.GetBasicBlockName(), caller);
        IrBuilder.SetCurrentFunction(caller);
        IrBuilder.SetCurrentBasicBlock(temporary);
        try {
            AllocateInstr result = call.DefValue() ? new AllocateInstr(call.GetIrType()) : null;
            if (result != null) {
                IrBasicBlock entry = caller.GetBasicBlocks().get(0);
                result.SetInBasicBlock(entry);
                entry.GetInstrList().add(0, result);
            }
            // Forward references are remapped after every cloned definition is known.
            List<Instr> patch = new ArrayList<>();
            for (IrBasicBlock block : callee.GetBasicBlocks()) {
                IrBuilder.SetCurrentBasicBlock((IrBasicBlock) values.get(block));
                for (Instr instr : block.GetInstrList()) {
                    Instr copy;
                    if (instr instanceof ReturnInstr ret) {
                        if (result != null) patch.add(new StoreInstr(ret.GetReturnValue(), result));
                        new JumpInstr(continuation);
                        continue;
                    } else if (instr instanceof BranchInstr branch) {
                        copy = new BranchInstr(branch.GetCond(), branch.GetTrueBlock(), branch.GetFalseBlock());
                    } else if (instr instanceof JumpInstr jump) copy = new JumpInstr(jump.GetTargetBlock());
                    else copy = SmallFunctionInline.copy(instr, Map.of());
                    values.put(instr, copy);
                    patch.add(copy);
                }
            }
            for (Instr instr : patch) {
                List<IrValue> operands = instr.GetUseValueList();
                for (int i = 0; i < operands.size(); i++) {
                    IrValue old = operands.get(i), replacement = values.get(old);
                    if (replacement != null && replacement != old) {
                        old.DeleteUser(instr);
                        operands.set(i, replacement);
                        replacement.AddUse(new IrUse(instr, replacement));
                    }
                }
            }
            int index = origin.GetInstrList().indexOf(call);
            List<Instr> suffix = new ArrayList<>(origin.GetInstrList().subList(index + 1, origin.GetInstrList().size()));
            origin.GetInstrList().subList(index, origin.GetInstrList().size()).clear();
            IrBuilder.SetCurrentBasicBlock(continuation);
            if (result != null) call.ModifyAllUsersToNewValue(new LoadInstr(result));
            for (Instr instr : suffix) continuation.AddInstr(instr);
            IrSupport.detach(call);
            IrBuilder.SetCurrentBasicBlock(origin);
            new JumpInstr(clones.get(0));
            clones.add(continuation);
            caller.GetBasicBlocks().addAll(caller.GetBasicBlocks().indexOf(origin) + 1, clones);
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }
}
