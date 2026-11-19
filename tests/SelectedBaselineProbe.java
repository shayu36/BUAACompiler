import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import midend.MidEnd;
import midend.llvm.instr.Instr;
import midend.llvm.use.IrUser;
import midend.llvm.value.IrValue;

/** Validate live IR; the selected baseline retains users from removed functions. */
public final class SelectedBaselineProbe {
    public static void main(String[] args) throws Exception {
        Compiler.main(args);
        if (MidEnd.GetIrModule() == null) return;
        Set<Instr> active = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<IrValue,Map<IrUser,Integer>> expected = new IdentityHashMap<>();
        for (var function : MidEnd.GetIrModule().GetFunctions()) for (var block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr.GetInBasicBlock() != block) throw new AssertionError("Instruction owner");
                active.add(instr);
                expected.computeIfAbsent(instr,k -> new IdentityHashMap<>());
                for (IrValue operand : instr.GetUseValueList()) if (operand != null)
                    expected.computeIfAbsent(operand,k -> new IdentityHashMap<>()).merge(instr,1,Integer::sum);
            }
        }
        for (var entry : expected.entrySet()) {
            if (entry.getKey() instanceof Instr definition && !active.contains(definition)) throw new AssertionError("Deleted definition");
            Map<IrUser,Integer> actual = new IdentityHashMap<>();
            for (var use : entry.getKey().GetUseList()) if (use.GetUser() instanceof Instr user && active.contains(user)) {
                if (use.GetValue() != entry.getKey()) throw new AssertionError("Use value mismatch");
                actual.merge(user,1,Integer::sum);
            }
            if (!actual.equals(entry.getValue())) throw new AssertionError("Live use edges: "+entry.getKey().GetIrName());
        }
        Files.writeString(Path.of("optimized.ll"),MidEnd.GetIrModule().toString());
    }
}
