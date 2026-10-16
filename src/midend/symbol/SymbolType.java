package midend.symbol;

public enum SymbolType {
    CHAR("Char"),
    INT("Int"),
    
    CHAR_ARRAY("CharArray"),
    INT_ARRAY("IntArray"),
    
    CONST_CHAR("ConstChar"),
    STATIC_CHAR("StaticChar"),
    CONST_INT("ConstInt"),
    STATIC_INT("StaticInt"),
    
    CONST_CHAR_ARRAY("ConstCharArray"),
    STATIC_CHAR_ARRAY("StaticCharArray"),
    CONST_INT_ARRAY("ConstIntArray"),
    STATIC_INT_ARRAY("StaticIntArray"),
    
    VOID_FUNC("VoidFunc"),
    CHAR_FUNC("CharFunc"),
    INT_FUNC("IntFunc"),
    
    ERROR("Error");
    private final String typeName;
    
    SymbolType(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public String toString() {
        return typeName;
    }

    public static SymbolType GetConstType(String type, int dimension) {
        if(type.equals("int")){
              if(dimension == 0){
                  return CONST_INT;
              }
              else{
                  return CONST_INT_ARRAY;
              }
        }
        else if(type.equals("char")){
            if(dimension == 0){
                return CONST_CHAR;
            }
            else{
                return CONST_CHAR_ARRAY;
            }
        }
        else{
            return ERROR;
        }
    }

    public static SymbolType GetVarType(String type, int dimension, boolean isStatic) {
        if (type.equals("int")) {
            if (isStatic) {
                return dimension == 0 ? STATIC_INT : STATIC_INT_ARRAY;
            }
            return dimension == 0 ? INT : INT_ARRAY;
        } else if (type.equals("char")) {
            if (isStatic) {
                return dimension == 0 ? STATIC_CHAR : STATIC_CHAR_ARRAY;
            }
            return dimension == 0 ? CHAR : CHAR_ARRAY;
        } else {
            return ERROR;
        }
    }
    
    public static SymbolType GetFuncType(String type){
        switch(type){
            case "void":
                return VOID_FUNC;
            case "int":
                return INT_FUNC;
            case "char":
                return CHAR_FUNC;
            default:
                return ERROR;
        }
    }

    public boolean IsConst() {
        return this == CONST_INT || this == CONST_CHAR ||
            this == CONST_INT_ARRAY || this == CONST_CHAR_ARRAY;
    }
}
