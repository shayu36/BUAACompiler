package midend.symbol;

import java.util.ArrayList;

public class FuncSymbol extends Symbol {
    private ArrayList<Symbol> formalParamList;
    
    public FuncSymbol(String symbolName, SymbolType symbolType, ArrayList<Symbol> formalParamList) {
        super(symbolName, symbolType);
        this.formalParamList = formalParamList;
    }
    
    public ArrayList<Symbol> GetFormalParamList() {
        return formalParamList;
    }
    
    public void SetFormalParamList(ArrayList<Symbol> formalParamList) {
        this.formalParamList = formalParamList;
    }

    public boolean isIO() {
        return switch (this.GetSymbolName()) {
            case "get_int", "get_char", "get_string",
                 "put_int", "put_char", "put_string", "put_str" -> true;
            default -> false;
        };
    }
}
