package frontend.ast.func;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.Exp;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

import java.util.ArrayList;

public class FuncRealParamS extends Node {
    // FuncRParams -> Exp { ',' Exp }
    public FuncRealParamS() {
        super(SyntaxType.FUNC_REAL_PARAM_S);
    }
    
    @Override
    public void Parse() {
        // Exp
        AddNode(new Exp());
        while (GetCurrentTokenType().equals(TokenType.COMMA)) {
            // ,
            AddNode(new TokenNode());
            // Exp
            AddNode(new Exp());
        }
    }
    
    public ArrayList<Exp> GetRealParamList() {
        ArrayList<Exp> realParamList = new ArrayList<>();
        for (Node component : components) {
            if (component instanceof Exp) {
                realParamList.add((Exp) component);
            }
        }
        return realParamList;
    }
}
