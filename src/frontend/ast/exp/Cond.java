package frontend.ast.exp;

import frontend.ast.SyntaxType;
import frontend.ast.exp.recursion.LOrExp;

public class Cond extends ComputeExp {
    // Cond -> LOrExp
    public Cond() {
        super(SyntaxType.COND_EXP);
    }

    @Override
    public void Parse() {
        AddNode(new LOrExp());
    }
    
    @Override
    public void Compute() {
        // 递归进行计算
        LOrExp lorExp = (LOrExp) this.components.get(0);
        lorExp.Compute();
        // 设置相应的属性
        this.isConst = lorExp.GetIsConst();
        this.value = lorExp.GetValue();
    }

    @Override
    public String GetExpType() {
        String type = ((LOrExp) components.get(0)).GetExpType();
        if ("ERROR".equals(type)) {
            return "ERROR";
        }
        return "INT".equals(type) || "CHAR".equals(type) ? "INT" : type;
    }

    public LOrExp GetLOrExp() {
        return (LOrExp) this.components.get(0);
    }
}
