package frontend.ast.exp;

import frontend.ast.SyntaxType;
import frontend.ast.exp.recursion.AddExp;

public class Exp extends ComputeExp {
    // Exp -> AddExp
    public Exp() {
        super(SyntaxType.EXP);
    }

    @Override
    public void Parse() {
        AddNode(new AddExp());
    }
    
    @Override
    public void Compute() {
        // 递归进行计算
        AddExp addExp = (AddExp) this.components.get(0);
        addExp.Compute();
        // 设置相应的属性
        this.isConst = addExp.GetIsConst();
        this.value = addExp.GetValue();
    }
    
    public AddExp GetAddExp() {
        return (AddExp)this.components.get(0);
    }
    
    @Override
    public String GetExpType() {
        return ((AddExp)(components.get(0))).GetExpType();
    }
}
