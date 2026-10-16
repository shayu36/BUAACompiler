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
        super.Visit();
        String name = ((Ident) this.components.get(0)).GetTokenString();
        //获取维数dimension
        int dimension = (this.components.size() > 3) ? 1 : 0;
        ArrayList<Integer> depthList = new ArrayList<Integer>();
        ArrayList<Integer> initValueList = new ArrayList<Integer>();
        if (dimension == 1) {
            //解析表达式求值
            ;
        }
        int line = ((Ident) this.components.get(0)).GetTokenLine();
        SymbolManger.AddSymbol(new ValueSymbol(name,
            SymbolType.GetConstType(this.typeString, dimension),
            dimension, depthList, initValueList), line);
    }
    
    public void setTypeString(String typeString) {
        this.typeString = typeString;
    }
}
