package frontend.ast.exp;

import frontend.ast.SyntaxType;
import frontend.ast.exp.recursion.AddExp;

public class Exp extends ComputeExp {
    public Exp() {
        super(SyntaxType.EXP);
    }

    @Override
    public void Parse() {
        AddNode(new AddExp());
    }

    @Override
    public void Compute() {
    }
}
