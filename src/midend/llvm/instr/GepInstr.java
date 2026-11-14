package midend.llvm.instr;

import midend.llvm.type.IrArrayType;
import midend.llvm.type.IrPointerType;
import midend.llvm.type.IrType;
import midend.llvm.value.IrValue;

public class GepInstr extends Instr {
    public GepInstr(IrValue pointer, IrValue offset) {
        super(new IrPointerType(GetTargetType(pointer)), InstrType.GEP);
        AddUseValue(pointer);
        AddUseValue(offset);
    }

    @Override
    public boolean DefValue() {
        return true;
    }

    public static IrType GetTargetType(IrValue pointer) {
        IrType targetType = ((IrPointerType) pointer.GetIrType()).getTargetType();
        if (targetType instanceof IrArrayType arrayType) {
            return arrayType.GetElementType();
        } else {
            return targetType;
        }
    }

    @Override
    public String toString() {
        IrValue pointer = useValueList.get(0);
        IrValue offset = useValueList.get(1);
        IrPointerType pointerType = (IrPointerType) pointer.GetIrType();
        IrType targetType = pointerType.getTargetType();
        if (targetType instanceof IrArrayType) {
            return this.irName + " = getelementptr inbounds " +
                targetType + ", " +
                pointerType + " " +
                pointer.GetIrName() + ", i32 0, " +
                offset.GetIrType() + " " +
                offset.GetIrName();
        } else {
            return this.irName + " = getelementptr inbounds " +
                targetType + ", " +
                pointerType + " " +
                pointer.GetIrName() + ", " +
                offset.GetIrType() + " " +
                offset.GetIrName();
        }
    }
}
