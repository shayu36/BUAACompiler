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
        // '(' Exp ')'
        if (IsExpType()) {
            ComputeExp exp = (ComputeExp) this.components.get(1);
            exp.Compute();
            this.isConst = exp.GetIsConst();
            this.value = exp.GetValue();
        }
        // Number
        else if (IsNumberType()) {
            this.isConst = true;
            this.value = GetNumber().GetCaseNumber();
        }
        // LVal
        else {
            ComputeExp lVal = (ComputeExp) this.components.get(0);
            lVal.Compute();
            this.isConst = lVal.GetIsConst();
            this.value = lVal.GetValue();
        }
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

    public boolean IsExpType() {
        return this.components.get(0) instanceof TokenNode;
    }

    public boolean IsLValType() {
        return this.components.get(0) instanceof LVal;
    }

    public boolean IsNumberType() {
        return this.components.get(0) instanceof Number;
    }

    public Exp GetExp() {
        return (Exp) this.components.get(1);
    }

    public LVal GetLVal() {
        return (LVal) this.components.get(0);
    }

    public Number GetNumber() {
        return (Number) this.components.get(0);
    }
}
