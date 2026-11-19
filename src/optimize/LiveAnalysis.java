package optimize;

import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** Local scalar storage is treated as a variable, SSA results as single definitions. */
public final class LiveAnalysis {
    public final ControlFlow flow;
    public final Set<IrValue> slots;
    public final Map<IrBasicBlock, Set<IrValue>> in = new LinkedHashMap<>();
    public final Map<IrBasicBlock, Set<IrValue>> out = new LinkedHashMap<>();
    private final Map<IrBasicBlock, Set<IrValue>> uses = new LinkedHashMap<>();
    private final Map<IrBasicBlock, Set<IrValue>> defs = new LinkedHashMap<>();

    public LiveAnalysis(IrFunction function) {
        flow = new ControlFlow(function);
        slots = IrSupport.scalarSlots(function);
        for (IrBasicBlock block : flow.blocks) {
            Set<IrValue> use = new LinkedHashSet<>(), def = new LinkedHashSet<>();
            for (Instr instr : block.GetInstrList()) {
                for (IrValue value : uses(instr)) if (!def.contains(value)) use.add(value);
                IrValue definition = definition(instr);
                if (definition != null) def.add(definition);
            }
            uses.put(block, use);
            defs.put(block, def);
            in.put(block, new LinkedHashSet<>());
            out.put(block, new LinkedHashSet<>());
        }
        boolean changed;
        do {
            changed = false;
            for (int i = flow.blocks.size() - 1; i >= 0; i--) {
                IrBasicBlock block = flow.blocks.get(i);
                Set<IrValue> next = new LinkedHashSet<>();
                for (IrBasicBlock successor : flow.successors.get(block)) next.addAll(in.get(successor));
                Set<IrValue> before = new LinkedHashSet<>(next);
                before.removeAll(defs.get(block));
                before.addAll(uses.get(block));
                if (!next.equals(out.get(block)) || !before.equals(in.get(block))) changed = true;
                out.put(block, next);
                in.put(block, before);
            }
        } while (changed);
    }

    public IrValue definition(Instr instr) {
        if (instr instanceof StoreInstr && slots.contains(instr.GetUseValueList().get(1))) {
            return instr.GetUseValueList().get(1);
        }
        return instr.DefValue() && !(instr instanceof AllocateInstr) ? instr : null;
    }

    public Set<IrValue> uses(Instr instr) {
        Set<IrValue> result = new LinkedHashSet<>();
        List<IrValue> args = instr.GetUseValueList();
        for (int i = 0; i < args.size(); i++) {
            IrValue value = args.get(i);
            if (instr instanceof StoreInstr && i == 1 && slots.contains(value)) continue;
            if (value instanceof IrParameter || value instanceof Instr) result.add(value);
        }
        return result;
    }
}
