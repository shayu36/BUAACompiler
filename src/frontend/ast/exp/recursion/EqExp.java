package frontend.ast.exp.recursion;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class EqExp extends RecursionNode {
    public EqExp() {
        super(SyntaxType.EQ_EXP);
    }

    // 重建用：把单个操作数包成 EqExp（文法左边必须是 EqExp）
    public EqExp(Node node) {
        super(SyntaxType.EQ_EXP);
        this.components.add(node);
    }

    // 重建用：把 (左子树, 运算符, 操作数) 组成新 EqExp
    public EqExp(Node node1, Node node2, Node node3) {
        super(SyntaxType.EQ_EXP);
        this.components.add(node1);
        this.components.add(node2);
        this.components.add(node3);
    }

    /*
     * 原文法（左递归，不能直接写成递归下降）：
     *     EqExp → RelExp | EqExp ('==' | '!=') RelExp
     * 改写（平铺形式，消掉左递归）：
     *     EqExp → RelExp { ('==' | '!=') RelExp }
     * 平铺得到 nodeList = [RelExp, op, RelExp, op, ...]，
     * 再由 HandleRecursion 重建成左结合树 ((a == b) != c)。
     */
    @Override
    public void Parse() {
        AddNodeList(new RelExp());
        while(GetCurrentTokenType().equals(TokenType.EQL) ||
            GetCurrentTokenType().equals(TokenType.NEQ)){
            AddNodeList(new TokenNode());
            AddNodeList(new RelExp());
        }
        HandleRecursion(EqExp::new, EqExp::new);
    }
}
