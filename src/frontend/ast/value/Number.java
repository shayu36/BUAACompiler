package frontend.ast.value;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.CharConst;
import frontend.ast.token.IntConst;
import frontend.lexer.TokenType;

public class Number extends Node {
    public Number() {
        super(SyntaxType.NUMBER);
    }

    @Override
    public void Parse() {
        // IntConst
        if(GetCurrentTokenType().equals(TokenType.INTCON)){
            AddNode(new IntConst());
        }
        // CharConst
        else{
            AddNode(new CharConst());
        }
    }
}
