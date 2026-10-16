package midend.symbol;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;

import java.util.ArrayList;
import java.util.Hashtable;

public class SymbolTable {
    private final ArrayList<Symbol> symbolList;//按照声明顺序收集
    private final Hashtable<String, Symbol> symbolTable;//快速找到某个符号的Symbol
    SymbolTable fatherTable;
    private int scopeId;//作用域序号
    private ArrayList<SymbolTable> sonTables;//子作用域
    
    SymbolTable(int scopeId, SymbolTable fatherTable) {
        this.scopeId = scopeId;
        this.fatherTable = fatherTable;
        this.sonTables = new ArrayList<>();
        this.symbolTable = new Hashtable<>();
        this.symbolList = new ArrayList<>();
    }
    
    public void addSymbol(Symbol symbol, int line) {
        if (symbolTable.containsKey(symbol.GetSymbolName())) {
            ErrorRecorder.AddError(new Error(ErrorType.NAME_REDEFINE,line));
        } else {
            this.symbolList.add(symbol);
            symbolTable.put(symbol.GetSymbolName(), symbol);
        }
    }
    
    public void addSonTable(SymbolTable sonSymbolTable) {
        this.sonTables.add(sonSymbolTable);
    }
    
    public SymbolTable getFatherTable() {
        return fatherTable;
    }
    
    public Symbol getSymbolInCurrentScope(String name) {
        return symbolTable.get(name);
    }
    
    public int getScopeId() {
        return scopeId;
    }
    
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Symbol symbol : this.symbolList) {
            stringBuilder.append(this.scopeId + " " + symbol + "\n");
        }
        
        for (SymbolTable sonTable : this.sonTables) {
            stringBuilder.append(sonTable.toString());
        }
        
        return stringBuilder.toString();
    }
}
