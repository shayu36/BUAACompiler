package frontend.ast.exp.recursion;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class LOrExp extends RecursionNode {
    public LOrExp() {
        super(SyntaxType.LOR_EXP);
    }

    // 重建用：把单个操作数包成 LOrExp（文法左边必须是 LOrExp）
    public LOrExp(Node node) {
        super(SyntaxType.LOR_EXP);
        this.components.add(node);
    }

    // 重建用：把 (左子树, 运算符, 操作数) 组成新 LOrExp
    public LOrExp(Node node1, Node node2, Node node3) {
        super(SyntaxType.LOR_EXP);
        this.components.add(node1);
        this.components.add(node2);
        this.components.add(node3);
    }

    /*
     * 原文法（左递归，不能直接写成递归下降）：
     *     LOrExp → LAndExp | LOrExp '||' LAndExp
     * 改写（平铺形式，消掉左递归）：
     *     LOrExp → LAndExp { '||' LAndExp }
     * 平铺得到 nodeList = [LAndExp, '||', LAndExp, '||', ...]，
     * 再由 HandleRecursion 重建成左结合树 ((a || b) || c)。
     */
    @Override
    public void Parse() {
        // LAndExp
        AddNodeList(new LAndExp());
        while(GetCurrentTokenType().equals(TokenType.OR)){
            // ||
            AddNodeList(new TokenNode());
            // LAndExp
            AddNodeList(new LAndExp());
        }
        HandleRecursion(LOrExp::new, LOrExp::new);
    }
}
