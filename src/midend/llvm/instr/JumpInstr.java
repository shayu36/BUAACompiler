package midend.llvm.instr;

import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrBasicBlock;

public class JumpInstr extends Instr {
    public JumpInstr(IrBasicBlock targetBlock) {
        super(IrBaseType.VOID, InstrType.JUMP, "jump");
        AddUseValue(targetBlock);
    }

    //targetBlock br到哪里  createBlock  我在哪个块里
    public JumpInstr(IrBasicBlock targetBlock, IrBasicBlock createBlock) {
        super(IrBaseType.VOID, InstrType.JUMP, "jump", false);//关闭塞进当前块
        AddUseValue(targetBlock);
        this.SetInBasicBlock(createBlock);//塞进之前的空块里
    }

    public IrBasicBlock GetTargetBlock() {
        return (IrBasicBlock) useValueList.get(0);
    }

    @Override
    public boolean DefValue() {
        return false;
    }

    @Override
    public String toString() {
        IrBasicBlock targetBlock = this.GetTargetBlock();
        return "br label %" + targetBlock.GetIrName();
    }
}
