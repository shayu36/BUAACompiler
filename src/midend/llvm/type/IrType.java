package midend.llvm.type;

public abstract class IrType {
    
    public boolean IsArrayType() {
        return this instanceof IrArrayType;
    }
    
    public boolean IsInt1Type() {
        return this == IrBaseType.INT1;
    }
    
    public boolean IsInt8Type() {
        return this == IrBaseType.INT8;
    }
    
    public boolean IsInt32Type() {
        return this == IrBaseType.INT32;
    }
    
    public boolean IsVoidType() {
        return this == IrBaseType.VOID;
    }
    
    public boolean IsPointerType() {
        return this instanceof IrPointerType;
    }
    
    public boolean IsBasicBlockType() {
        return this instanceof IrBasicBlockType;
    }
    
    public boolean IsFunctionType() {
        return this instanceof IrFunctionType;
    }
    
    public abstract String toString();
}
