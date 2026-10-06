package frontend.ast.func;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.block.Block;
import frontend.ast.token.FuncType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class FuncDef extends Node {
    public FuncDef() {
        super(SyntaxType.FUNC_DEF);
    }

    @Override
    public void Parse() {
        AddNode(new FuncType());
        AddNode(new Ident());
        AddNode(new TokenNode());//(
        if(!GetCurrentToken().GetTokenType().equals(TokenType.RPARENT)){
            AddNode(new FuncFormalParamS());
        }
        if(GetCurrentTokenType().equals(TokenType.RPARENT)){
            AddNode(new TokenNode());//)
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
        }
        AddNode(new Block());
    }
}
