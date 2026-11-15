package frontend.ast.decl;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.ast.value.InitVal;
import frontend.lexer.TokenType;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;
import midend.symbol.ValueSymbol;

import java.util.ArrayList;

public class VarDef extends Node {
    // VarDef -> Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal
    private String varType;//int/char
    private boolean isStatic;
    
    public VarDef() {
        super(SyntaxType.VAR_DEF);
        this.isStatic = false;
    }
    
    @Override
    public void Parse() {
        // Ident
        AddNode(new Ident());
        // [ ConstExp ]
        if (GetCurrentTokenType().equals(TokenType.LBRACK)) {
            // [
            AddNode(new TokenNode());
            // ConstExp
            AddNode(new ConstExp());
            // ]
            if (GetCurrentTokenType().equals(TokenType.RBRACK)) {
                AddNode(new TokenNode());
            } else {
                AddMissRBrackError();
            }
        }
        // = InitVal
        if (GetCurrentTokenType().equals(TokenType.ASSIGN)) {
            // =
            AddNode(new TokenNode());
            // InitVal
            AddNode(new InitVal());
        }
    }
    
    @Override
    public void Visit() {
        Ident ident = this.GetIdent();
        String symbolName = ident.GetTokenString();

        ArrayList<Integer> depthList = new ArrayList<>();
        for (Node component : this.components) {
            if (component instanceof ConstExp constExp) {
                constExp.Visit();
                constExp.Compute();
                depthList.add(constExp.GetValue());
            }
        }

        int dimension = depthList.size();
        SymbolType symbolType = SymbolType.GetVarType(this.varType, dimension, this.isStatic);
        ValueSymbol symbol = new ValueSymbol(symbolName, symbolType, dimension, depthList, null);
        // 声明在自己的初始化表达式中可见。
        SymbolManger.AddSymbol(symbol, ident.GetTokenLine());

        InitVal initVal = this.GetInitVal();
        if (initVal != null) {
            initVal.Visit();
            if (SymbolManger.IsGlobal() || this.isStatic) {
                symbol.GetInitValueList().addAll(initVal.GetInitValueList());
            }
        }
    }
    
    public void setVarType(String varType) {
        this.varType = varType;
    }
    
    public void setIsStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }
    
    public Ident GetIdent() {
        return (Ident) this.components.get(0);
    }
    
    public boolean HaveInitVal() {
        for (Node component : this.components) {
            if (component instanceof InitVal) {
                return true;
            }
        }
        return false;
    }
    
    public InitVal GetInitVal() {
        for (Node component : this.components) {
            if (component instanceof InitVal initVal) {
                return initVal;
            }
        }
        return null;
    }
}
