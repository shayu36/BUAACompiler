package frontend.ast.func;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.BType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class FuncFormalParam extends Node {
    public  FuncFormalParam() {
        super(SyntaxType.FUNC_FORMAL_PARAM);
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
                ErrorRecorder.AddError(new Error(ErrorType.MISS_RBRACK, Peek(-1).GetLineNumber()));
            }
        }
    }
}
