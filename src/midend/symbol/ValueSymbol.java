package midend.symbol;

import java.util.ArrayList;

public class ValueSymbol extends Symbol {
    private final int dimension;//变量维数 0/1
    private final ArrayList<Integer> depthList;//数组长度
    private final ArrayList<Integer> initValueList;//初值
    private boolean isGlobal;
    
    private boolean isConst;
    private ArrayList<Integer> valueList;//存储计算出的值
    
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
}
