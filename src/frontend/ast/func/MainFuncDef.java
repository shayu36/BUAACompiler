package frontend.ast.func;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.block.Block;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class MainFuncDef extends Node {
    public MainFuncDef() {
        super(SyntaxType.MAIN_FUNC_DEF);
    }

    @Override
    public void Parse() {
        AddNode(new TokenNode());//int
        AddNode(new TokenNode());//main
        AddNode(new TokenNode());//(
        if(GetCurrentTokenType().equals(TokenType.RPARENT)){
            AddNode(new TokenNode());//)
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
        }
        AddNode(new Block());
    }
}
