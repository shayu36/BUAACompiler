package frontend.ast.decl;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.BType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class VarDecl extends Node {
    public VarDecl() {
        super(SyntaxType.VAR_DECL);
    }

    @Override
    public void Parse() {
        //[static]
        if(GetCurrentTokenType().equals(TokenType.STATICTK)) {
            this.AddNode(new TokenNode());
        }
        this.AddNode(new BType());
        this.AddNode(new VarDef());
        while(GetCurrentTokenType().equals(TokenType.COMMA)) {
            this.AddNode(new TokenNode());//,
            this.AddNode(new VarDef());
        }
        
        if(GetCurrentTokenType().equals(TokenType.SEMICN)){
            this.AddNode(new TokenNode());//;
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
        }
    }
}
