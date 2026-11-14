package midend.llvm.instr.io;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrValue;

public class PrintCharInstr extends IoInstr {
    public PrintCharInstr(IrValue printValue) {
        super(IrBaseType.VOID);
        this.AddUseValue(printValue);
    }

    public IrValue GetPrintValue() {
        return this.useValueList.get(0);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrValue printValue = this.GetPrintValue();
        return "call void @put_char(i8 " + printValue.GetIrName() + ")";
    }
}
