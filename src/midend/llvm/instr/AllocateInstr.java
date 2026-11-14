package midend.llvm.instr;

import midend.llvm.type.IrBaseType;
import midend.llvm.type.IrPointerType;
import midend.llvm.type.IrType;

public class AllocateInstr extends Instr {
    private final IrType targetType;
    public AllocateInstr(IrType targetType) {
        super(new IrPointerType(targetType), InstrType.ALLOCATE);
        this.targetType = targetType;
    }

    public AllocateInstr(IrType targetType, boolean autoAdd) {
        super(new IrPointerType(targetType), InstrType.ALLOCATE, autoAdd);
        this.targetType = targetType;
    }
    public IrType GetTargetType() {
        return targetType;
    }

    @Override
    public boolean DefValue(){
        return true;
    }
    
    @Override
    public String toString() {
        return this.irName + " = alloca " + this.targetType;
    }
    
}
