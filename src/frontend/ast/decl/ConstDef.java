package frontend.ast.decl;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.ast.value.ConstInitVal;
import frontend.lexer.TokenType;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;
import midend.symbol.ValueSymbol;

import java.util.ArrayList;

public class ConstDef extends Node {
    // ConstDef -> Ident [ '[' ConstExp ']' ] '=' ConstInitVal
    private String typeString;
    
    public ConstDef() {
        super(SyntaxType.CONST_DEF);
    }
    
    @Override
    public void Parse() {
        AddNode(new Ident());
        if (GetCurrentTokenType().equals(TokenType.LBRACK)) {
            AddNode(new TokenNode());//[
            AddNode(new ConstExp());
            if (GetCurrentTokenType().equals(TokenType.RBRACK)) {
                AddNode(new TokenNode());//]
            } else {
                AddMissRBrackError();
            }
        }
        AddNode(new TokenNode());//=
        AddNode(new ConstInitVal());
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
            } else if (component instanceof ConstInitVal constInitVal) {
                initValueList = constInitVal.GetInitValueList();
            }
        }

        ValueSymbol symbol = new ValueSymbol(symbolName,
            SymbolType.GetConstType(this.typeString, dimension),
            dimension, depthList, initValueList);
        // 设置const值
        symbol.SetValueList(initValueList);
        symbol.SetIsConst(true);

        int line = ident.GetTokenLine();
        SymbolManger.AddSymbol(symbol, line);
    }
    
    public void setTypeString(String typeString) {
        this.typeString = typeString;
    }

    public Ident GetIdent() {
        return (Ident) this.components.get(0);
    }
}
