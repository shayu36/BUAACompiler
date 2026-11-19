package optimize;

import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

public final class ControlFlow {
    public final List<IrBasicBlock> blocks;
    public final Map<IrBasicBlock, Set<IrBasicBlock>> successors = new LinkedHashMap<>();
    public final Map<IrBasicBlock, Set<IrBasicBlock>> predecessors = new LinkedHashMap<>();
    public final Map<IrBasicBlock, Set<IrBasicBlock>> dominators = new LinkedHashMap<>();

    public ControlFlow(IrFunction function) {
        blocks = function.GetBasicBlocks();
        for (IrBasicBlock block : blocks) {
            successors.put(block, new LinkedHashSet<>());
            predecessors.put(block, new LinkedHashSet<>());
        }
        for (IrBasicBlock block : blocks) {
            List<Instr> instructions = block.GetInstrList();
            if (instructions.isEmpty()) continue;
            Instr last = instructions.get(instructions.size() - 1);
            if (last instanceof JumpInstr jump) successors.get(block).add(jump.GetTargetBlock());
            if (last instanceof BranchInstr branch) {
                successors.get(block).add(branch.GetTrueBlock());
                successors.get(block).add(branch.GetFalseBlock());
            }
            for (IrBasicBlock next : successors.get(block)) predecessors.get(next).add(block);
        }
    }

    public void computeDominators() {
        IrBasicBlock entry = blocks.get(0);
        for (IrBasicBlock block : blocks) {
            dominators.put(block, new LinkedHashSet<>(block == entry ? List.of(entry) : blocks));
        }
        boolean changed;
        do {
            changed = false;
            for (IrBasicBlock block : blocks) {
                if (block == entry) continue;
                Set<IrBasicBlock> result = new LinkedHashSet<>(blocks);
                for (IrBasicBlock before : predecessors.get(block)) result.retainAll(dominators.get(before));
                result.add(block);
                if (!result.equals(dominators.get(block))) {
                    dominators.put(block, result);
                    changed = true;
                }
            }
        } while (changed);
    }
}
