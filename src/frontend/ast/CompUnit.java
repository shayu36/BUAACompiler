package frontend.ast;

import frontend.ast.decl.Decl;
import frontend.lexer.TokenType;
import frontend.ast.func.FuncDef;
import frontend.ast.func.MainFuncDef;

public class CompUnit extends Node {
    public CompUnit() {
        super(SyntaxType.COMP_UNIT);
    }
    
    @Override
    public void Parse() {
        while (GetCurrentToken() != null) {
            //主函数
            if (Peek(1).GetTokenType().equals(TokenType.MAINTK)) {
                this.AddNode(new MainFuncDef());
            }
            //函数定义
            else if (Peek(2).GetTokenType().equals(TokenType.LPARENT)) {
                this.AddNode(new FuncDef());
            }
            //声明
            else if (GetCurrentTokenType().equals(TokenType.CONSTTK) ||
                GetCurrentTokenType().equals(TokenType.INTTK) ||
                GetCurrentTokenType().equals(TokenType.CHARTK) ||
                GetCurrentTokenType().equals(TokenType.STATICTK)) {
                this.AddNode(new Decl());
            } else {
                break;
            }
        }
    }
}
