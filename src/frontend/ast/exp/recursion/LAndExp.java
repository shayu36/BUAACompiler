package frontend.ast.exp.recursion;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class LAndExp extends RecursionNode {
    public LAndExp() {
        super(SyntaxType.LAND_EXP);
    }

    // 重建用：把单个操作数包成 LAndExp（文法左边必须是 LAndExp）
    public LAndExp(Node node) {
        super(SyntaxType.LAND_EXP);
        this.components.add(node);
    }

    // 重建用：把 (左子树, 运算符, 操作数) 组成新 LAndExp
    public LAndExp(Node node1, Node node2, Node node3) {
        super(SyntaxType.LAND_EXP);
        this.components.add(node1);
        this.components.add(node2);
        this.components.add(node3);
    }

    /*
     * 原文法（左递归，不能直接写成递归下降）：
     *     LAndExp → EqExp | LAndExp '&&' EqExp
     * 改写（平铺形式，消掉左递归）：
     *     LAndExp → EqExp { '&&' EqExp }
     * 平铺得到 nodeList = [EqExp, '&&', EqExp, '&&', ...]，
     * 再由 HandleRecursion 重建成左结合树 ((a && b) && c)。
     */
    @Override
    public void Parse() {
        AddNodeList(new EqExp());
        while(GetCurrentTokenType().equals(TokenType.AND)){
            AddNodeList(new TokenNode());
            AddNodeList(new EqExp());
        }
        HandleRecursion(LAndExp::new, LAndExp::new);
    }
}
