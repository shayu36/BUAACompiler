package frontend.ast.value;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.Exp;
import frontend.ast.token.StringConst;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class InitVal extends Node {
    public InitVal() {
        super(SyntaxType.INIT_VAL);
    }

    @Override
    public void Parse() {
        // {...} 形式的初值
        if(GetCurrentTokenType().equals(TokenType.LBRACE)){
            //{
            AddNode(new TokenNode());
            // 空表 {} 时直接跳到 }
            if(!GetCurrentTokenType().equals(TokenType.RBRACE)){
                // Exp
                AddNode(new Exp());
                while(GetCurrentTokenType().equals(TokenType.COMMA)){
                    // ,
                    AddNode(new TokenNode());
                    // Exp
                    AddNode(new Exp());
                }
            }
            //}
            AddNode(new TokenNode());
        }
        // StringConst
        else if(GetCurrentTokenType().equals(TokenType.STRCON)){
            AddNode(new StringConst());
        }
        // Exp
        else{
            AddNode(new Exp());
        }
    }
}
