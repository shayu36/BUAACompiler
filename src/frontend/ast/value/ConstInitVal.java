package frontend.ast.value;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.token.StringConst;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class ConstInitVal extends Node {
    public ConstInitVal() {
        super(SyntaxType.CONST_INIT_VAL);
    }

    @Override
    public void Parse() {
        // {...} 形式的初值
        if(GetCurrentTokenType().equals(TokenType.LBRACE)){
            //{
            AddNode(new TokenNode());
            // 空表 {} 时直接跳到 }
            if(!GetCurrentTokenType().equals(TokenType.RBRACE)){
                // ConstExp
                AddNode(new ConstExp());
                while(GetCurrentTokenType().equals(TokenType.COMMA)){
                    // ,
                    AddNode(new TokenNode());
                    // ConstExp
                    AddNode(new ConstExp());
                }
            }
            //}
            AddNode(new TokenNode());
        }
        // StringConst
        else if(GetCurrentTokenType().equals(TokenType.STRCON)){
            AddNode(new StringConst());
        }
        // ConstExp
        else{
            AddNode(new ConstExp());
        }
    }
}
