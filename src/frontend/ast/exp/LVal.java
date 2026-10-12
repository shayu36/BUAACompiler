package frontend.ast.exp;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.SyntaxType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class LVal extends ComputeExp {
    public LVal() {
        super(SyntaxType.LVAL_EXP);
    }

    @Override
    public void Parse() {
        // Ident
        AddNode(new Ident());
        // [ Exp ]
        if(GetCurrentTokenType().equals(TokenType.LBRACK)){
            AddNode(new TokenNode());//[
            AddNode(new Exp());
            if(GetCurrentTokenType().equals(TokenType.RBRACK)){
                AddNode(new TokenNode());//]
            } else {
                AddMissRBrackError();
            }
        }
    }

    @Override
    public void Compute() {
    }
}
