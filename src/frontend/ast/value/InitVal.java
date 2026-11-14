package frontend.ast.value;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.Exp;
import frontend.ast.token.StringConst;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

import java.util.ArrayList;

public class InitVal extends Node {
    // InitVal -> Exp | '{' [ Exp { ',' Exp } ] '}' | StringConst
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

    public boolean HaveStringConst() {
        return this.components.get(0) instanceof StringConst;
    }

    public StringConst GetStringConst() {
        return (StringConst) this.components.get(0);
    }

    public ArrayList<Exp> GetExpList() {
        ArrayList<Exp> expList = new ArrayList<>();
        for (Node component : this.components) {
            if (component instanceof Exp exp) {
                expList.add(exp);
            }
        }
        return expList;
    }

    // 约定如果是常值形式，那么返回只有一个元素的list
    // 如果是字符串形式，那么是一个ASCII的list
    public ArrayList<Integer> GetInitValueList() {
        ArrayList<Integer> initValueList = new ArrayList<>();
        for (Node component : this.components) {
            // exp形式
            if (component instanceof Exp exp) {
                exp.Compute();
                initValueList.add(exp.GetValue());
            }
            // string形式
            else if (component instanceof StringConst stringConst) {
                // token 带首尾引号（"global"），剥掉引号再逐字符处理
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
