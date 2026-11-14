package midend.llvm.instr;

import midend.llvm.value.IrValue;

public class AluInstr extends Instr {
    String opName;

    public AluInstr(String op, IrValue value1, IrValue value2) {
        super(value1.GetIrType(), InstrType.ALU);
        this.opName = GetInstrName(op);
        AddUseValue(value1);
        AddUseValue(value2);
    }

    public String GetInstrName(String op) {
        return switch (op) {
            case "+" -> "add";
            case "-" -> "sub";
            case "*" -> "mul";
            case "/" -> "sdiv";
            case "%" -> "srem";
            case "udiv" -> "udiv";
            case "urem" -> "urem";
            default -> throw new RuntimeException("illegal alu op");
        };
    }

    @Override
    public boolean DefValue() {
        return true;
    }

    @Override
    public String toString() {
        IrValue value1 = useValueList.get(0);
        IrValue value2 = useValueList.get(1);
        return irName + " = " + opName + " " + value1.GetIrType() + " " +
            value1.GetIrName() + ", " + value2.GetIrName();
    }
}
