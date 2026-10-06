package frontend.ast.exp;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.SyntaxType;
import frontend.ast.func.FuncRealParamS;
import frontend.ast.token.BType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class UnaryExp extends ComputeExp {
    public UnaryExp() {
        super(SyntaxType.UNARY_EXP);
    }
    
    @Override
    public void Parse() {
        // 函数调用：Ident '(' [FuncRParams] ')'
        if (GetCurrentTokenType().equals(TokenType.IDENFR) &&
            Peek(1).GetTokenType().equals(TokenType.LPARENT)) {
            // Ident
            AddNode(new Ident());
            // (
            AddNode(new TokenNode());
            if (!GetCurrentTokenType().equals(TokenType.RPARENT)) {
                // FuncRParams
                AddNode(new FuncRealParamS());
            }
            // )
            if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
                AddNode(new TokenNode());
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
            }
        }
        //'(' BType ')' UnaryExp
        else if (GetCurrentTokenType().equals(TokenType.LPARENT) &&
            (Peek(1).GetTokenType().equals(TokenType.INTTK) ||
                Peek(1).GetTokenType().equals(TokenType.CHARTK))) {
            AddNode(new TokenNode());//(
            AddNode(new BType());
            if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
                AddNode(new TokenNode());//)
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
            }
            AddNode(new UnaryExp());
        }
        // 一元运算符：UnaryOp UnaryExp
        else if (GetCurrentTokenType().equals(TokenType.PLUS) ||
            GetCurrentTokenType().equals(TokenType.MINU) ||
            GetCurrentTokenType().equals(TokenType.NOT)) {
            // UnaryOp
            AddNode(new UnaryOp());
            // UnaryExp
            AddNode(new UnaryExp());
        }
        // PrimaryExp
        else {
            AddNode(new PrimaryExp());
        }
    }
    
    @Override
    public void Compute() {
    }
}
