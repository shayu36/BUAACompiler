package frontend.ast.exp;

import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.ast.value.Number;
import frontend.lexer.TokenType;

public class PrimaryExp extends ComputeExp {
    // PrimaryExp -> '(' Exp ')' | LVal | Number
    public PrimaryExp() {
        super(SyntaxType.PRIMARY_EXP);
    }
    
    @Override
    public void Parse() {
        // '(' Exp ')'
        if (GetCurrentTokenType().equals(TokenType.LPARENT)) {
            AddNode(new TokenNode());//(
            AddNode(new Exp());
            if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
                AddNode(new TokenNode());//)
            } else {
                AddMissRParentError();
            }
        } else if (GetCurrentTokenType().equals(TokenType.INTCON) ||
            GetCurrentTokenType().equals(TokenType.CHARCON)) {
            AddNode(new Number());
        }
        // LVal
        else {
            AddNode(new LVal());
        }
    }
    
    @Override
    public void Compute() {
    }
    
    @Override
    public String GetExpType() {
        if (components.get(0) instanceof LVal lval) {
            return lval.GetExpType();
        } else if (components.get(0) instanceof Number number) {
            return number.GetExpType();
        } else {
            return ((Exp) (components.get(1))).GetExpType();
        }
    }
}
