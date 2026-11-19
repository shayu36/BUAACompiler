package midend.llvm.constant;

import midend.llvm.type.IrBaseType;
import midend.llvm.type.IrType;

public class IrConstantInt extends IrConstant {
    private final int value;

    public IrConstantInt(int value) {
        super(IrBaseType.INT32, String.valueOf(value));
        this.value = value;
    }

    public int GetValue() {
        return this.value;
    }

    /*后端/优化*/
    public IrConstantInt(int value, IrType type) {
        super(type, String.valueOf(value));
        this.value = value;
    }
    /*后端/优化*/

    @Override
    public String toString() {
        return this.GetIrType() + " " + this.value;
    }
}
