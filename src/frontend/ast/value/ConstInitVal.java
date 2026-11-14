package frontend.ast.value;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.token.StringConst;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

import java.util.ArrayList;

public class ConstInitVal extends Node {
    // ConstInitVal -> ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}' | StringConst
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

    // 约定如果是常值形式，那么返回只有一个元素的list
    // 如果是字符串形式，那么是一个ASCII的list
    public ArrayList<Integer> GetInitValueList() {
        ArrayList<Integer> initValueList = new ArrayList<>();
        for (Node component : this.components) {
            // constexp形式
            if (component instanceof ConstExp constExp) {
                constExp.Compute();
                initValueList.add(constExp.GetValue());
            }
            // string形式
            else if (component instanceof StringConst stringConst) {
                // token 带首尾引号（"hello"），剥掉引号再逐字符处理
                String token = stringConst.GetTokenString();
                String string = token.substring(1, token.length() - 1);
                ArrayList<Integer> asciiList = new ArrayList<>();
                for (int i = 0; i < string.length(); i++) {
                    char ch = string.charAt(i);
                    if (ch == '\\' && i < string.length() - 1 && string.charAt(i + 1) == 'n') {
                        asciiList.add((int) ('\n'));
                        i++;
                    } else {
                        asciiList.add((int) ch);
                    }
                }
                return asciiList;
            }
        }
        return initValueList;
    }
}
