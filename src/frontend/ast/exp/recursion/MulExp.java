package frontend.ast.exp.recursion;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.UnaryExp;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class MulExp extends RecursionNode {
    public MulExp() {
        super(SyntaxType.MUL_EXP);
    }

    // 重建用：把单个操作数包成 MulExp（文法左边必须是 MulExp）
    public MulExp(Node node) {
        super(SyntaxType.MUL_EXP);
        this.components.add(node);
    }

    // 重建用：把 (左子树, 运算符, 操作数) 组成新 MulExp
    public MulExp(Node node1, Node node2, Node node3) {
        super(SyntaxType.MUL_EXP);
        this.components.add(node1);
        this.components.add(node2);
        this.components.add(node3);
    }

    /*
     * 原文法（左递归，不能直接写成递归下降）：
     *     MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
     * 改写（平铺形式，消掉左递归）：
     *     MulExp → UnaryExp { ('*' | '/' | '%') UnaryExp }
     * 平铺得到 nodeList = [UnaryExp, op, UnaryExp, op, ...]，
     * 再由 HandleRecursion 重建成左结合树 ((a * b) / c)。
     */
    @Override
    public void Parse() {
        // UnaryExp
        AddNodeList(new UnaryExp());
        while(GetCurrentTokenType().equals(TokenType.MULT) ||
            GetCurrentTokenType().equals(TokenType.DIV) ||
            GetCurrentTokenType().equals(TokenType.MOD)){
            // * | / | %
            AddNodeList(new TokenNode());
            // UnaryExp
            AddNodeList(new UnaryExp());
        }
        HandleRecursion(MulExp::new, MulExp::new);
    }
}
