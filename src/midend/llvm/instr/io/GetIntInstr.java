package midend.llvm.instr.io;

import midend.llvm.type.IrBaseType;

public class GetIntInstr extends IoInstr {
    public GetIntInstr() {
        super(IrBaseType.INT32);
    }

    @Override
    public boolean DefValue() {
        return true;
    }

    @Override
    public String toString() {
        return this.irName + " = call i32 @get_int()";
    }
}
