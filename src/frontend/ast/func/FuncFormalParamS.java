package frontend.ast.func;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class FuncFormalParamS extends Node {
    public FuncFormalParamS() {
        super(SyntaxType.FUNC_FORMAL_PARAM_S);
    }

    @Override
    public void Parse() {
        // FuncFParam
        AddNode(new FuncFormalParam());
        while(GetCurrentTokenType().equals(TokenType.COMMA)){
            // ,
            AddNode(new TokenNode());
            // FuncFParam
            AddNode(new FuncFormalParam());
        }
    }
}
