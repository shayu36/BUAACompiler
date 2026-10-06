package frontend.ast.decl;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ConstExp;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.ast.value.ConstInitVal;
import frontend.lexer.TokenType;

public class ConstDef extends Node {
    public ConstDef() {
        super(SyntaxType.CONST_DEF);
    }

    @Override
    public void Parse() {
        AddNode(new Ident());
        if(GetCurrentTokenType().equals(TokenType.LBRACK)){
            AddNode(new TokenNode());//[
            AddNode(new ConstExp());
            if(GetCurrentTokenType().equals(TokenType.RBRACK)){
                AddNode(new TokenNode());//]
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_RBRACK, Peek(-1).GetLineNumber()));
            }
        }
        AddNode(new TokenNode());//=
        AddNode(new ConstInitVal());
    }
}
