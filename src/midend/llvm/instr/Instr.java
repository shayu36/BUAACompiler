package midend.llvm.instr;

import midend.llvm.IrBuilder;
import midend.llvm.type.IrType;
import midend.llvm.use.IrUser;
import midend.llvm.value.IrBasicBlock;

// instr是一种User：使用其他的Value作为参数
public abstract class Instr extends IrUser {
    private final InstrType instrType;
    private IrBasicBlock inBasicBlock;

    public Instr(IrType irType, InstrType instrType) {
        super(irType, IrBuilder.GetLocalVarName());
        this.instrType = instrType;
        // 自动插入
        IrBuilder.AddInstr(this);
    }

    public Instr(IrType irType, InstrType instrType, boolean autoAdd) {
        super(irType, IrBuilder.GetLocalVarName());
        this.instrType = instrType;
        // 自动插入
        if (autoAdd) {
            IrBuilder.AddInstr(this);
        }
    }

    public Instr(IrType irType, InstrType instrType, String irName) {
        super(irType, irName);
        this.instrType = instrType;
        // 自动插入
        IrBuilder.AddInstr(this);
    }

    public Instr(IrType irType, InstrType instrType, String irName, boolean autoAdd) {
        super(irType, irName);
        this.instrType = instrType;
        // 自动插入
        if (autoAdd) {
            IrBuilder.AddInstr(this);
        }
    }

    public void SetInBasicBlock(IrBasicBlock irBasicBlock) {
        this.inBasicBlock = irBasicBlock;
    }

    public InstrType GetInstrType() {
        return this.instrType;
    }

    public IrBasicBlock GetInBasicBlock() {
        return this.inBasicBlock;
    }

    public abstract boolean DefValue();

    @Override
    public abstract String toString();
}
