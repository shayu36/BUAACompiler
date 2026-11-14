package midend.llvm.instr.io;

import midend.llvm.type.IrBaseType;

public class GetCharInstr extends IoInstr {
    public GetCharInstr() {
        super(IrBaseType.INT8);
    }

    @Override
    public boolean DefValue() {
        return true;
    }

    @Override
    public String toString() {
        return this.irName + " = call i8 @get_char()";
    }
}
