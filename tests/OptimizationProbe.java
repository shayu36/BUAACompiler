import java.nio.file.Files;
import java.nio.file.Path;
import midend.MidEnd;
import midend.llvm.instr.Instr;
import midend.llvm.value.IrValue;
import midend.llvm.use.IrUse;
import midend.llvm.use.IrUser;
import java.util.*;

/** Run the real entry point, then expose its final in-memory IR for verification. */
public class OptimizationProbe {
    public static void main(String[] args) throws Exception {
        Compiler.main(args);
        if (MidEnd.GetIrModule() != null) {
            verifyUses();
            Files.writeString(Path.of("optimized.ll"), MidEnd.GetIrModule().toString());
        }
    }

    private static void verifyUses() {
        Set<Instr> active = new HashSet<>();
        Map<IrValue, Map<IrUser,Integer>> expected = new IdentityHashMap<>();
        for (var function : MidEnd.GetIrModule().GetFunctions()) {
            for (var block : function.GetBasicBlocks()) {
                for (Instr instr : block.GetInstrList()) {
                    if (instr.GetInBasicBlock() != block) throw new AssertionError("Wrong instruction owner");
                    active.add(instr);
                    expected.computeIfAbsent(instr, unused -> new IdentityHashMap<>());
                    for (IrValue operand : instr.GetUseValueList()) {
                        if (operand != null) expected.computeIfAbsent(operand, unused -> new IdentityHashMap<>()).merge(instr,1,Integer::sum);
                    }
                }
            }
        }
        for (var entry : expected.entrySet()) {
            if (entry.getKey() instanceof Instr definition && !active.contains(definition)) {
                throw new AssertionError("Use of deleted definition: " + definition.GetIrName());
            }
            Map<IrUser,Integer> actual = new IdentityHashMap<>();
            for (IrUse use : entry.getKey().GetUseList()) {
                if (use.GetValue() != entry.getKey()) throw new AssertionError("Wrong use edge value");
                actual.merge(use.GetUser(),1,Integer::sum);
            }
            if (!actual.equals(entry.getValue())) throw new AssertionError("Stale use edges: " + entry.getKey().GetIrName());
        }
    }
}
