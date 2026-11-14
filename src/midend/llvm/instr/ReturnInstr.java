package midend.llvm.instr;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrValue;

public class ReturnInstr extends Instr {
    public ReturnInstr(IrValue returnValue) {
        super(IrBaseType.VOID, InstrType.RETURN, "return");
        AddUseValue(returnValue);
    }

    public IrValue GetReturnValue() {
        return useValueList.isEmpty() ? null : useValueList.get(0);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrValue returnValue = this.GetReturnValue();

        return "ret " + (returnValue == null ? "void" :
            returnValue.GetIrType() + " " + returnValue.GetIrName());
    }
}
