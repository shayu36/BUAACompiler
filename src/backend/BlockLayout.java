package backend;

import midend.llvm.instr.BranchInstr;
import midend.llvm.value.*;
import optimize.ControlFlow;
import java.util.*;

/** Put a unique backedge immediately before its loop test. Initial entry still jumps to the test. */
final class BlockLayout {
    static List<IrBasicBlock> order(IrFunction function) {
        ControlFlow flow = new ControlFlow(function);
        flow.computeDominators();
        List<IrBasicBlock> result = new ArrayList<>(flow.blocks);
        for (IrBasicBlock header : flow.blocks) {
            if (header == flow.blocks.get(0) || header.GetInstrList().isEmpty() ||
                !(header.GetInstrList().get(header.GetInstrList().size() - 1) instanceof BranchInstr)) continue;
            List<IrBasicBlock> tails = flow.predecessors.get(header).stream()
                .filter(b -> flow.dominators.get(b).contains(header)).toList();
            if (tails.size() != 1 || tails.get(0) == header) continue;
            IrBasicBlock tail = tails.get(0);
            if (flow.successors.get(tail).size() != 1) continue;
            result.remove(header);
            result.add(result.indexOf(tail) + 1, header);
        }
        return result;
    }
}
