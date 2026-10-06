package frontend.ast.exp;

import frontend.ast.SyntaxType;
import frontend.ast.exp.recursion.LOrExp;

public class Cond extends ComputeExp {
    public Cond() {
        super(SyntaxType.COND_EXP);
    }

    @Override
    public void Parse() {
        AddNode(new LOrExp());
    }

    @Override
    public void Compute() {
    }
}
