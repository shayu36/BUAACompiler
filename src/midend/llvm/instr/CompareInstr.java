package midend.llvm.instr;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrValue;

public class CompareInstr extends Instr {
    public enum CompareOp {
        EQ,
        NE,
        SGT,
        SGE,
        SLT,
        SLE,
        UGT,
        UGE,
        ULT,
        ULE
    }
    private final CompareOp opName;
    public CompareInstr(String op, IrValue value1, IrValue value2) {
        super(IrBaseType.INT1, InstrType.CMP);
        this.opName = GetStringToCompareOp(op);
        AddUseValue(value1);
        AddUseValue(value2);
    }

    private CompareOp GetStringToCompareOp(String op) {
        return switch (op) {
            case "==" -> CompareOp.EQ;
            case "!=" -> CompareOp.NE;
            case ">=" -> CompareOp.SGE;
            case ">" -> CompareOp.SGT;
            case "<=" -> CompareOp.SLE;
            case "<" -> CompareOp.SLT;
            case "ugt" -> CompareOp.UGT;
            case "uge" -> CompareOp.UGE;
            case "ult" -> CompareOp.ULT;
            case "ule" -> CompareOp.ULE;
            default -> throw new RuntimeException("illegal compare op");
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
        return irName + " = icmp " + opName.toString().toLowerCase() + " " +
            value1.GetIrType() + " " + value1.GetIrName() + ", " + value2.GetIrName();
    }

    /*后端/优化*/

    public CompareOp GetCompareOp() {
        return this.opName;
    }
    /*后端/优化*/
}
