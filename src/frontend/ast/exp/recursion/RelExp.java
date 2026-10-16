package frontend.ast.exp.recursion;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ComputeExp;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class RelExp extends RecursionNode {
    // RelExp -> AddExp | RelExp ( '<' | '>' | '<=' | '>=' ) AddExp
    public RelExp() {
        super(SyntaxType.REL_EXP);
    }

    // 重建用：把单个操作数包成 RelExp（文法左边必须是 RelExp）
    public RelExp(Node node) {
        super(SyntaxType.REL_EXP);
        this.components.add(node);
    }

    // 重建用：把 (左子树, 运算符, 操作数) 组成新 RelExp
    public RelExp(Node node1, Node node2, Node node3) {
        super(SyntaxType.REL_EXP);
        this.components.add(node1);
        this.components.add(node2);
        this.components.add(node3);
    }

    /*
     * 原文法（左递归，不能直接写成递归下降）：
     *     RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
     * 改写（平铺形式，消掉左递归）：
     *     RelExp → AddExp { ('<' | '>' | '<=' | '>=') AddExp }
     * 平铺得到 nodeList = [AddExp, op, AddExp, op, ...]，
     * 再由 HandleRecursion 重建成左结合树 ((a < b) > c)。
     */
    @Override
    public void Parse() {
        // AddExp
        AddNodeList(new AddExp());
        while(GetCurrentTokenType().equals(TokenType.LSS) ||
            GetCurrentTokenType().equals(TokenType.LEQ) ||
            GetCurrentTokenType().equals(TokenType.GRE) ||
            GetCurrentTokenType().equals(TokenType.GEQ)){
            // < | > | <= | >=
            AddNodeList(new TokenNode());
            // AddExp
            AddNodeList(new AddExp());
        }
        HandleRecursion(RelExp::new, RelExp::new);
    }
    
    @Override
    public String GetExpType() {
        if (components.size() == 1) {
            return ((ComputeExp) components.get(0)).GetExpType();
        }
        
        ComputeExp left = (ComputeExp) components.get(0);
        ComputeExp right = (ComputeExp) components.get(2);
        String leftType = left.GetExpType();
        String rightType = right.GetExpType();
        
        if ("ERROR".equals(leftType) || "ERROR".equals(rightType)) {
            return "ERROR";
        }
        
        boolean leftIsBasic = leftType.equals("INT") || leftType.equals("CHAR");
        boolean rightIsBasic = rightType.equals("INT") || rightType.equals("CHAR");
        if (!leftIsBasic || !rightIsBasic || !leftType.equals(rightType)) {
            TokenNode operator = (TokenNode) components.get(1);
            ErrorRecorder.AddError(
                new Error(
                    ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                    operator.GetTokenLine()
                )
            );
            return "ERROR";
        }
        
        return "INT";
    }
}
