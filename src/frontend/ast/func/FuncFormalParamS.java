package frontend.ast.func;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;
import midend.symbol.Symbol;

import java.util.ArrayList;

public class FuncFormalParamS extends Node {
    // FuncFParams -> FuncFParam { ',' FuncFParam }
    public FuncFormalParamS() {
        super(SyntaxType.FUNC_FORMAL_PARAM_S);
    }

    @Override
    public void Parse() {
        // FuncFParam
        AddNode(new FuncFormalParam());
        while(GetCurrentTokenType().equals(TokenType.COMMA)){
            // ,
            AddNode(new TokenNode());
            // FuncFParam
            AddNode(new FuncFormalParam());
        }
    }
    
    public ArrayList<Symbol> GetFormalParamList(){
        ArrayList<Symbol> formalParamList = new ArrayList<>();
        for(Node component:this.components){
            if(component instanceof FuncFormalParam formalParam){
                formalParamList.add(formalParam.GetSymbol());
            }
        }
        return formalParamList;
    }
}
