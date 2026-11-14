package midend.llvm.instr;

import midend.llvm.type.IrPointerType;
import midend.llvm.value.IrValue;

public class LoadInstr extends Instr {
    public LoadInstr(IrValue pointer) {
        super(((IrPointerType) pointer.GetIrType()).getTargetType(), InstrType.LOAD);
        AddUseValue(pointer);
    }

    @Override
    public boolean DefValue() {
        return true;
    }

    @Override
    public String toString() {
        IrValue pointer = useValueList.get(0);
        return irName+" = load "+irType+", "+pointer.GetIrType()+" "+pointer.GetIrName();
    }
}
