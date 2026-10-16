package frontend.ast.exp;

import frontend.ast.SyntaxType;
import frontend.ast.exp.recursion.AddExp;

public class ConstExp extends ComputeExp {
    // ConstExp -> AddExp
    public ConstExp() {
        super(SyntaxType.CONST_EXP);
    }

    @Override
    public void Parse() {
        AddNode(new AddExp());
    }

    @Override
    public void Compute() {
    }
    
    @Override
    public String GetExpType() {
        return ((AddExp) components.get(0)).GetExpType();
    }
}
