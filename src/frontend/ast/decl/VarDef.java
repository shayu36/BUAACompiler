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
    private String typeString;
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
        super.Visit();
        Ident ident = (Ident) this.components.get(0);
        String name = ident.GetTokenString();
        // 获取维数 dimension
        int dimension = (this.components.size() > 3) ? 1 : 0;
        ArrayList<Integer> depthList = new ArrayList<>();
        ArrayList<Integer> initValueList = new ArrayList<>();
        if (dimension == 1) {
            // 解析数组长度表达式
            ;
        }
        SymbolType symbolType = SymbolType.GetVarType(
            this.typeString,
            dimension,
            this.isStatic
        );
        int line = ident.GetTokenLine();
        SymbolManger.AddSymbol(new ValueSymbol(
            name,
            symbolType,
            dimension,
            depthList,
            initValueList
        ), line);
        
    }
    
    public void setTypeString(String typeString) {
        this.typeString = typeString;
    }
    
    public void setIsStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }
}
