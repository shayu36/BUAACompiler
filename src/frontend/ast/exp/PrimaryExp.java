package frontend.ast.exp;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.ast.value.Number;
import frontend.lexer.TokenType;

public class PrimaryExp extends ComputeExp {
    public PrimaryExp() {
        super(SyntaxType.PRIMARY_EXP);
    }

    @Override
    public void Parse() {
        // '(' Exp ')'
        if(GetCurrentTokenType().equals(TokenType.LPARENT)){
            AddNode(new TokenNode());//(
            AddNode(new Exp());
            if(GetCurrentTokenType().equals(TokenType.RPARENT)){
                AddNode(new TokenNode());//)
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
            }
        }
        // Number → IntConst | CharConst
        else if(GetCurrentTokenType().equals(TokenType.INTCON) ||
            GetCurrentTokenType().equals(TokenType.CHARCON)){
            AddNode(new Number());
        }
        // LVal
        else{
            AddNode(new LVal());
        }
    }

    @Override
    public void Compute() {
    }
}
