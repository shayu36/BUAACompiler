package frontend.ast.func;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.BType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;
import midend.symbol.ValueSymbol;

import java.util.ArrayList;

public class FuncFormalParam extends Node {
    // FuncFParam -> BType Ident [ '[' ']' ]
    private Symbol symbol;

    public  FuncFormalParam() {
        super(SyntaxType.FUNC_FORMAL_PARAM);
    }
    
    public Symbol GetSymbol() {
        return this.symbol;
    }

    @Override
    public void Parse() {
        // BType
        AddNode(new BType());
        // Ident
        AddNode(new Ident());
        // [ '[' ']' ]
        if(GetCurrentTokenType().equals(TokenType.LBRACK)){
            // [
            AddNode(new TokenNode());
            // ]
            if(GetCurrentTokenType().equals(TokenType.RBRACK)){
                AddNode(new TokenNode());
            } else {
                AddMissRBrackError();
            }
        }
    }
    
    @Override
    public void Visit() {
        super.Visit();
        String type = ((BType) this.components.get(0)).GetTokenString();
        Ident ident = (Ident) this.components.get(1);
        String symbolName = ident.GetTokenString();
        int dimension = 0;
        for (Node component : this.components) {
            if (component instanceof TokenNode tokenNode &&
                tokenNode.GetTokenString().equals("[")) {
                dimension++;
            }
        }
        this.symbol = new ValueSymbol(
            symbolName,
            SymbolType.GetVarType(type, dimension, false),
            dimension,
            new ArrayList<>(),
            new ArrayList<>()
        );
        int line = ident.GetTokenLine();
        SymbolManger.AddSymbol(this.symbol,line);
    }
}
