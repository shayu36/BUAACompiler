package frontend.ast.decl;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.exp.Exp;
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
        Ident ident = (Ident) this.components.get(0);
        String symbolName = ident.GetTokenString();

        int dimension = 0;
        ArrayList<Integer> depthList = new ArrayList<>();
        ArrayList<Integer> initValueList = null;
        // 获取维度：判断是否有维度信息
        for (Node component : this.components) {
            component.Visit();
            if (component instanceof ConstExp constExp) {
                dimension++;
                // 计算维度信息
                constExp.Compute();
                depthList.add(constExp.GetValue());
            } else if (component instanceof InitVal initVal) {
                initValueList = initVal.GetInitValueList();
            }
        }

        SymbolType symbolType = SymbolType.GetVarType(
            this.varType,
            dimension,
            this.isStatic
        );
        int line = ident.GetTokenLine();
        SymbolManger.AddSymbol(SymbolManger.IsGlobal() || this.isStatic ?
            new ValueSymbol(symbolName, symbolType, dimension, depthList, initValueList) :
            new ValueSymbol(symbolName, symbolType, dimension, depthList, null), line);
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
