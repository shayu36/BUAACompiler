package frontend.ast.decl;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.ast.value.InitVal;
import frontend.lexer.TokenType;

public class VarDef extends Node {
    public VarDef() {
        super(SyntaxType.VAR_DEF);
    }

    @Override
    public void Parse() {
        // Ident
        AddNode(new Ident());
        // [ ConstExp ]
        if(GetCurrentTokenType().equals(TokenType.LBRACK)){
            // [
            AddNode(new TokenNode());
            // ConstExp
            AddNode(new ConstExp());
            // ]
            if(GetCurrentTokenType().equals(TokenType.RBRACK)){
                AddNode(new TokenNode());
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_RBRACK, Peek(-1).GetLineNumber()));
            }
        }
        // = InitVal
        if(GetCurrentTokenType().equals(TokenType.ASSIGN)){
            // =
            AddNode(new TokenNode());
            // InitVal
            AddNode(new InitVal());
        }
    }
}
