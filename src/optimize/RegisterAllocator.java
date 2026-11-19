package optimize;

import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** Interference coloring with callee-saved colors for values live across calls. */
public final class RegisterAllocator {
    private static final List<String> SAVED = List.of("$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7");
    // t0-t2 are instruction scratch registers, t3 handles large stack offsets.
    private static final List<String> TEMPORARY = List.of("$t4", "$t5", "$t6", "$t7", "$t8", "$t9");
    private static final List<String> ARGUMENTS = List.of("$a0", "$a1", "$a2", "$a3");
    public final Map<IrValue, String> registers = new LinkedHashMap<>();
    public final Set<IrValue> slots;
    public final List<String> savedRegisters;

    public RegisterAllocator(IrFunction function) {
        LiveAnalysis analysis = new LiveAnalysis(function);
        slots = analysis.slots;
        Map<IrValue, Set<IrValue>> graph = new LinkedHashMap<>();
        Map<IrValue, Integer> references = new HashMap<>();
        Map<IrValue, Set<IrValue>> copies = new HashMap<>();
        Set<IrValue> acrossCalls = new HashSet<>();
        boolean copyAware = !System.getProperty("compiler.copyAware","on").equalsIgnoreCase("off");
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            Set<IrValue> live = new LinkedHashSet<>(analysis.out.get(block));
            List<Instr> instructions = block.GetInstrList();
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Instr instr = instructions.get(i);
                IrValue definition = analysis.definition(instr);
                IrValue copySource = null;
                if (copyAware && instr instanceof LoadInstr && instr.GetIrType().IsInt32Type() &&
                    slots.contains(instr.GetUseValueList().get(0))) copySource = instr.GetUseValueList().get(0);
                if (copyAware && instr instanceof StoreInstr && slots.contains(definition) &&
                    instr.GetUseValueList().get(0).GetIrType().IsInt32Type() &&
                    ((AllocateInstr)definition).GetTargetType().IsInt32Type()) copySource = instr.GetUseValueList().get(0);
                if (instr instanceof midend.llvm.instr.LoadInstr && slots.contains(instr.GetUseValueList().get(0))) {
                    preferCopy(copies, instr, instr.GetUseValueList().get(0));
                } else if (instr instanceof midend.llvm.instr.StoreInstr && slots.contains(definition)) {
                    preferCopy(copies, definition, instr.GetUseValueList().get(0));
                }
                if (candidate(definition)) {
                    graph.computeIfAbsent(definition, ignored -> new LinkedHashSet<>());
                    // At an exact i32 copy the two names hold the same bits. They
                    // may share a color; any later write still adds interference
                    // with the old value if that value remains live.
                    for (IrValue other : live) if (other != copySource) connect(graph, definition, other);
                    live.remove(definition);
                }
                if (IrSupport.isCall(instr)) acrossCalls.addAll(live);
                for (IrValue use : analysis.uses(instr)) {
                    if (candidate(use)) {
                        graph.computeIfAbsent(use, ignored -> new LinkedHashSet<>());
                        references.merge(use, 1, Integer::sum);
                        live.add(use);
                    }
                }
            }
            // Parameters have implicit definitions before entry.
            if (block == function.GetBasicBlocks().get(0)) {
                for (IrValue value : live) for (IrValue other : live) connect(graph, value, other);
            }
        }
        List<IrValue> order = new ArrayList<>(graph.keySet());
        order.sort(Comparator.<IrValue>comparingInt(v -> acrossCalls.contains(v) ? 1 : 0).reversed()
            .thenComparing(Comparator.<IrValue>comparingInt(v -> references.getOrDefault(v, 0)).reversed())
            .thenComparing(IrValue::GetIrName));
        Map<IrValue, String> chosen = color(order, graph, copies, acrossCalls, Map.of(), List.of());
        boolean leafArguments = !System.getProperty("compiler.leafArguments", "on").equalsIgnoreCase("off") &&
            function.GetBasicBlocks().stream().flatMap(b -> b.GetInstrList().stream()).noneMatch(IrSupport::isCall);
        if (leafArguments) {
            Map<IrValue, String> incoming = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(4, function.GetParameterList().size()); i++) {
                IrValue parameter = function.GetParameterList().get(i);
                if (graph.containsKey(parameter)) incoming.put(parameter, ARGUMENTS.get(i));
            }
            Map<IrValue, String> proposed = color(order, graph, copies, acrossCalls, incoming, ARGUMENTS);
            if (preservesAllocation(function, chosen, proposed, copies)) chosen = proposed;
        }
        registers.putAll(chosen);
        savedRegisters = SAVED.stream().filter(registers::containsValue).toList();
    }

    private static Map<IrValue, String> color(List<IrValue> order, Map<IrValue, Set<IrValue>> graph,
            Map<IrValue, Set<IrValue>> copies, Set<IrValue> acrossCalls,
            Map<IrValue, String> fixed, List<String> extraColors) {
        Map<IrValue, String> allocation = new LinkedHashMap<>(fixed);
        for (IrValue value : order) {
            if (allocation.containsKey(value)) continue;
            Set<String> occupied = new HashSet<>();
            for (IrValue neighbor : graph.get(value)) occupied.add(allocation.get(neighbor));
            List<String> colors = new ArrayList<>();
            if (!acrossCalls.contains(value)) colors.addAll(TEMPORARY);
            colors.addAll(extraColors);
            colors.addAll(SAVED);
            List<String> preferred = new ArrayList<>();
            for (IrValue copy : copies.getOrDefault(value, Set.of())) {
                String color = allocation.get(copy);
                if (color != null && colors.contains(color) && !preferred.contains(color)) preferred.add(color);
            }
            colors.removeAll(preferred);
            colors.addAll(0, preferred);
            for (String color : colors) {
                if (!occupied.contains(color)) { allocation.put(value, color); break; }
            }
            // Values without a color retain their existing stack home.
        }
        return allocation;
    }

    private static boolean preservesAllocation(IrFunction function, Map<IrValue, String> old,
            Map<IrValue, String> proposed, Map<IrValue, Set<IrValue>> copies) {
        // Do not trade an existing register resident for another value, increase
        // the save/restore set, or introduce a move on an already coalesced copy.
        if (!proposed.keySet().containsAll(old.keySet())) return false;
        // A newly colored stack parameter adds an unconditional entry load,
        // even when its only use is on an untaken path or inside a zero-trip loop.
        for (int i = 4; i < function.GetParameterList().size(); i++) {
            IrValue parameter = function.GetParameterList().get(i);
            if (!old.containsKey(parameter) && proposed.containsKey(parameter)) return false;
        }
        if (SAVED.stream().filter(proposed::containsValue).count() > SAVED.stream().filter(old::containsValue).count()) return false;
        for (var entry : copies.entrySet()) for (IrValue other : entry.getValue()) {
            String before = old.get(entry.getKey());
            if (before != null && before.equals(old.get(other)) &&
                !Objects.equals(proposed.get(entry.getKey()), proposed.get(other))) return false;
        }
        for (IrBasicBlock block : function.GetBasicBlocks()) for (Instr instr : block.GetInstrList()) {
            if (instr.GetInstrType() != InstrType.EXTEND && instr.GetInstrType() != InstrType.TRUNC) continue;
            String before = old.get(instr);
            IrValue source = instr.GetUseValueList().get(0);
            if (before != null && before.equals(old.get(source)) && !Objects.equals(proposed.get(instr), proposed.get(source))) return false;
        }
        return true;
    }

    private boolean candidate(IrValue value) {
        return value instanceof IrParameter || (value instanceof Instr &&
            (!(value instanceof AllocateInstr) || slots.contains(value)));
    }

    private static void preferCopy(Map<IrValue, Set<IrValue>> copies, IrValue a, IrValue b) {
        copies.computeIfAbsent(a, ignored -> new LinkedHashSet<>()).add(b);
        copies.computeIfAbsent(b, ignored -> new LinkedHashSet<>()).add(a);
    }

    private void connect(Map<IrValue, Set<IrValue>> graph, IrValue left, IrValue right) {
        if (left == right || !candidate(left) || !candidate(right)) return;
        graph.computeIfAbsent(left, ignored -> new LinkedHashSet<>()).add(right);
        graph.computeIfAbsent(right, ignored -> new LinkedHashSet<>()).add(left);
    }
}
