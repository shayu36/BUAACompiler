package midend.llvm.instr.io;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrValue;

public class GetStringInstr extends IoInstr {
    public GetStringInstr(IrValue buffer, IrValue maxLen) {
        super(IrBaseType.VOID);
        this.AddUseValue(buffer);
        this.AddUseValue(maxLen);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrValue buffer = this.useValueList.get(0);
        IrValue maxLen = this.useValueList.get(1);
        return "call void @get_string(i8* " + buffer.GetIrName() +
            ", i32 " + maxLen.GetIrName() + ")";
    }
}
