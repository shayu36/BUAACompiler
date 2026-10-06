package frontend.ast.decl;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.BType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class ConstDecl extends Node {
    public ConstDecl() {
        super(SyntaxType.CONST_DECL);
    }
    
    @Override
    public void Parse() {
        AddNode(new TokenNode());//const
        AddNode(new BType());
        AddNode(new ConstDef());
        while (GetCurrentTokenType().equals(TokenType.COMMA)) {
            AddNode(new TokenNode());//,
            AddNode(new ConstDef());
        }
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            AddNode(new TokenNode());//;
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
        }
    }
}
