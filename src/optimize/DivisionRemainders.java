package optimize;

import midend.llvm.IrBuilder;
import midend.llvm.instr.*;
import midend.llvm.value.*;
import java.util.*;

/** Reuse a quotient already evaluated on this path: r = x - (x / d) * d. */
final class DivisionRemainders {
    private record Operand(IrValue value, Integer constant) {
        static Operand of(IrValue value) {
            return IrSupport.isNumber(value) ? new Operand(null,IrSupport.number(value)) : new Operand(value,null);
        }
    }
    private record Key(boolean signed, Operand dividend, Operand divisor) { }

    static void run(IrFunction function) {
        if (System.getProperty("compiler.divRemReuse","on").equalsIgnoreCase("off")) return;
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            Map<Key,AluInstr> quotients = new HashMap<>();
            for (Instr instr : new ArrayList<>(block.GetInstrList())) {
                if (!(instr instanceof AluInstr alu) || !instr.GetIrType().IsInt32Type()) continue;
                String op = alu.GetOpName();
                boolean divide = op.equals("sdiv") || op.equals("udiv");
                if (!divide && !op.equals("srem") && !op.equals("urem")) continue;
                IrValue dividend = instr.GetUseValueList().get(0), divisor = instr.GetUseValueList().get(1);
                if (IrSupport.isNumber(divisor) && IrSupport.number(divisor) == 0) continue;
                if (op.equals("urem") && IrSupport.isNumber(divisor)) {
                    int d = IrSupport.number(divisor);
                    // Power-of-two unsigned remainders already use an immediate
                    // mask (or a loaded mask). Preserve that cheaper lowering.
                    if ((d & (d-1)) == 0) continue;
                }
                Key key = new Key(op.charAt(0) == 's',Operand.of(dividend),Operand.of(divisor));
                if (divide) { quotients.put(key,alu); continue; }
                AluInstr quotient = quotients.get(key);
                if (quotient == null) continue;
                // Restrict to the same block: the division must really execute
                // before this remainder, including its existing fault behavior.
                IrBasicBlock temporary = new IrBasicBlock(IrBuilder.GetBasicBlockName(),function);
                IrFunction savedFunction = IrBuilder.GetCurFunctionIr();
                IrBasicBlock savedBlock = IrBuilder.GetCurrentBasicBlock();
                AluInstr result;
                IrBuilder.SetCurrentFunction(function);
                IrBuilder.SetCurrentBasicBlock(temporary);
                try {
                    IrValue product = new AluInstr("*",quotient,divisor);
                    result = new AluInstr("-",dividend,product);
                } finally {
                    IrBuilder.SetCurrentFunction(savedFunction);
                    IrBuilder.SetCurrentBasicBlock(savedBlock);
                }
                int index = block.GetInstrList().indexOf(instr);
                instr.ModifyAllUsersToNewValue(result);
                IrSupport.detach(instr);
                block.GetInstrList().remove(index);
                for (Instr generated : temporary.GetInstrList()) generated.SetInBasicBlock(block);
                block.GetInstrList().addAll(index,temporary.GetInstrList());
            }
        }
    }
}
