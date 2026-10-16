package midend;

import frontend.FrontEnd;
import frontend.ast.CompUnit;

import midend.symbol.SymbolManger;
import midend.symbol.SymbolTable;

public class MidEnd {
    private static CompUnit rootNode;
    
    public static void GenerateSymbolTable() {
        SymbolManger.Init();
        rootNode = FrontEnd.GetAstTree();
        rootNode.Visit();
        SymbolManger.GoBackToRootSymbolTable();
    }
    
    public static SymbolTable GetrootSymbolTable() {
        return SymbolManger.GetrootSymbolTable();
    }
}
