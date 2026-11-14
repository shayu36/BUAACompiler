package midend.llvm.instr;

import midend.llvm.type.IrType;
import midend.llvm.value.IrValue;

public class TruncInstr extends Instr {
    private final IrType targetType;

    public TruncInstr(IrValue originValue, IrType targetType) {
        super(targetType, InstrType.TRUNC);
        this.targetType = targetType;
        AddUseValue(originValue);
    }

    public IrType GetTargetType() {
        return this.targetType;
    }

    public IrType GetOriginType() {
        return GetOriginValue().GetIrType();
    }

    public IrValue GetOriginValue() {
        return useValueList.get(0);
    }

    @Override
    public boolean DefValue() {
        return true;
    }

    @Override
    public String toString() {
        IrValue originValue = GetOriginValue();
        return irName + " = trunc " + originValue.GetIrType() + " " +
            originValue.GetIrName() + " to " + this.targetType;
    }
}
