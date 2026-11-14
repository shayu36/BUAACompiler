package midend.llvm.instr;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrValue;

public class StoreInstr extends Instr {
    public StoreInstr(IrValue value, IrValue addr) {
        super(IrBaseType.VOID, InstrType.STORE);
        AddUseValue(value);
        AddUseValue(addr);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrValue value = useValueList.get(0);
        IrValue addr = useValueList.get(1);
        return "store " + value.GetIrType() + " " + value.GetIrName() + ", " +
            addr.GetIrType() + " " + addr.GetIrName();
    }
}
