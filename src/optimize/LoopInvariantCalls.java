package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.instr.*;
import midend.llvm.type.IrBaseType;
import midend.llvm.value.*;
import java.util.*;

/** Cache invariant pure calls on their first actual execution in a loop. */
public final class LoopInvariantCalls {
    private LoopInvariantCalls() { }

    public static void run(IrFunction function, FunctionEffects effects) {
        Set<CallInstr> cached = new HashSet<>();
        for (int round = 0; round < 12; round++) {
            ControlFlow flow = new ControlFlow(function);
            flow.computeDominators();
            boolean changed = false;
            for (var loop : loops(flow).entrySet()) {
                List<IrBasicBlock> predecessors = flow.predecessors.get(loop.getKey()).stream()
                    .filter(block -> !loop.getValue().contains(block)).toList();
                if (predecessors.size() != 1) continue;
                IrBasicBlock preheader = predecessors.get(0);
                if (flow.successors.get(preheader).size() != 1) continue;
                CallInstr candidate = findCall(loop.getValue(), preheader, flow, effects, cached);
                if (candidate != null) {
                    cache(candidate, function, preheader);
                    cached.add(candidate);
                    changed = true;
                    break;
                }
            }
            if (!changed) break;
        }
    }

    private static Map<IrBasicBlock, Set<IrBasicBlock>> loops(ControlFlow flow) {
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
        return loops;
    }

    private static CallInstr findCall(Set<IrBasicBlock> loop, IrBasicBlock preheader, ControlFlow flow,
                                      FunctionEffects effects, Set<CallInstr> cached) {
        for (IrBasicBlock block : loop) {
            for (Instr instr : block.GetInstrList()) {
                if (!(instr instanceof CallInstr call) || cached.contains(call) ||
                    !effects.isPure(call.GetTargetFunction())) continue;
                boolean invariant = true;
                for (IrValue argument : call.GetParamList()) {
                    if (argument instanceof Instr definition &&
                        !flow.dominators.get(preheader).contains(definition.GetInBasicBlock())) invariant = false;
                }
                if (invariant) return call;
            }
        }
        return null;
    }

    private static void cache(CallInstr call, IrFunction function, IrBasicBlock preheader) {
        IrBasicBlock original = call.GetInBasicBlock();
        IrBasicBlock compute = new IrBasicBlock(IrBuilder.GetBasicBlockName(), function);
        IrBasicBlock continuation = new IrBasicBlock(IrBuilder.GetBasicBlockName(), function);
        IrBasicBlock temporary = new IrBasicBlock(IrBuilder.GetBasicBlockName(), function);
        IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
        IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
        IrBuilder.SetCurrentFunction(function);
        IrBuilder.SetCurrentBasicBlock(temporary);
        try {
            AllocateInstr flag = new AllocateInstr(IrBaseType.INT32);
            AllocateInstr result = new AllocateInstr(call.GetIrType());
            IrBasicBlock entry = function.GetBasicBlocks().get(0);
            entry.AddInstrBeforeTerminator(flag);
            entry.AddInstrBeforeTerminator(result);
            StoreInstr initialize = new StoreInstr(IrSupport.constant(0, IrBaseType.INT32), flag);
            preheader.AddInstrBeforeTerminator(initialize);

            int callIndex = original.GetInstrList().indexOf(call);
            List<Instr> suffix = new ArrayList<>(original.GetInstrList().subList(callIndex + 1, original.GetInstrList().size()));
            original.GetInstrList().subList(callIndex, original.GetInstrList().size()).clear();
            IrBuilder.SetCurrentBasicBlock(continuation);
            LoadInstr cachedValue = new LoadInstr(result);
            call.ModifyAllUsersToNewValue(cachedValue);
            for (Instr instr : suffix) continuation.AddInstr(instr);

            IrBuilder.SetCurrentBasicBlock(original);
            LoadInstr initialized = new LoadInstr(flag);
            CompareInstr ready = new CompareInstr("!=", initialized, IrSupport.constant(0, IrBaseType.INT32));
            new BranchInstr(ready, continuation, compute);
            IrBuilder.SetCurrentBasicBlock(compute);
            compute.AddInstr(call);
            new StoreInstr(call, result);
            new StoreInstr(IrSupport.constant(1, IrBaseType.INT32), flag);
            new JumpInstr(continuation);
            int index = function.GetBasicBlocks().indexOf(original) + 1;
            function.GetBasicBlocks().add(index, compute);
            function.GetBasicBlocks().add(index + 1, continuation);
        } finally {
            IrBuilder.SetCurrentFunction(savedFunction);
            IrBuilder.SetCurrentBasicBlock(savedBlock);
        }
    }
}
