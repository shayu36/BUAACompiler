package midend.llvm.instr;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrBasicBlock;
import midend.llvm.value.IrValue;

public class BranchInstr extends Instr {
    public BranchInstr(IrValue cond, IrBasicBlock trueBlock, IrBasicBlock falseBlock) {
        super(IrBaseType.VOID, InstrType.BRANCH);
        AddUseValue(cond);
        AddUseValue(trueBlock);
        AddUseValue(falseBlock);
    }

    public IrValue GetCond() {
        return useValueList.get(0);
    }

    public IrBasicBlock GetTrueBlock() {
        return (IrBasicBlock) useValueList.get(1);
    }

    public IrBasicBlock GetFalseBlock() {
        return (IrBasicBlock) useValueList.get(2);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrValue cond = this.GetCond();
        IrBasicBlock trueBlock = this.GetTrueBlock();
        IrBasicBlock falseBlock = this.GetFalseBlock();

        return "br i1 " + cond.GetIrName() +
            ", label %" + trueBlock.GetIrName() +
            ", label %" + falseBlock.GetIrName();
    }
}
