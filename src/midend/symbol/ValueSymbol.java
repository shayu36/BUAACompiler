package midend.symbol;

import midend.llvm.type.IrType;
import midend.llvm.value.IrValue;

import java.util.ArrayList;

public class ValueSymbol extends Symbol {
    private final int dimension;//变量维数 0/1
    private final ArrayList<Integer> depthList;//数组长度
    private final ArrayList<Integer> initValueList;//初值
    private boolean isGlobal;
    
    private boolean isConst;
    private ArrayList<Integer> valueList;//存储计算出的值
    private IrValue irValue;
    
    public ValueSymbol(String symbolName, SymbolType symbolType, int dimension,
                       ArrayList<Integer> depthList, ArrayList<Integer> initValueList) {
        super(symbolName, symbolType);
        this.dimension = dimension;
        this.depthList = depthList;
        this.initValueList = initValueList == null ? new ArrayList<>() : initValueList;
        this.isGlobal = false;
        
        this.isConst = false;
        this.valueList = new ArrayList<>();
    }
    
    public IrValue getIrValue() {
        return irValue;
    }
    
    public void setIrValue(IrValue irValue) {
        this.irValue = irValue;
    }
    
    public int GetTotalDepth() {
        int totalDepth = 1;
        for (int depth : this.depthList) {
            totalDepth *= depth;
        }
        return totalDepth;
    }
    
    public int getDimension() {
        return dimension;
    }
    
    public IrType GetIrType() {
        return this.irValue.GetIrType();
    }
    
    public boolean IsConst() {
        return isConst;
    }
    
    public boolean IsGlobal() {
        return isGlobal;
    }
    
    public void SetIsConst(boolean isConst) {
        this.isConst = isConst;
    }
    
    public ArrayList<Integer> GetInitValueList() {
        return initValueList;
    }
    
    public ArrayList<Integer> GetDepthList() {
        return depthList;
    }
    
    public ArrayList<Integer> GetValueList() {
        return valueList;
    }
    
    public void SetValueList(ArrayList<Integer> valueList) {
        this.valueList = valueList;
    }
}
