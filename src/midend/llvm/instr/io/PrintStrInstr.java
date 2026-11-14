package midend.llvm.instr.io;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrValue;

public class PrintStrInstr extends IoInstr {
    public PrintStrInstr(IrValue strPointer) {
        super(IrBaseType.VOID);
        this.AddUseValue(strPointer);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrValue strPointer = this.useValueList.get(0);
        return "call void @put_string(i8* " + strPointer.GetIrName() + ")";
    }
}
