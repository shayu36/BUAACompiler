package frontend.ast.stmt;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.ast.value.Number;
import frontend.lexer.TokenType;

public class CaseStmt extends Node {
    public CaseStmt() {
        super(SyntaxType.CASE_STMT);
    }
    
    @Override
    public void Parse() {
        //'case' Number ':' { Stmt } | 'default' ':' { Stmt }
        if (GetCurrentTokenType().equals(TokenType.CASETK)) {
            // case
            AddNode(new TokenNode());
            // Number
            AddNode(new Number());
            // :
            AddNode(new TokenNode());
        } else {
            // default
            AddNode(new TokenNode());
            // :
            AddNode(new TokenNode());
        }
        // { Stmt } —— 直到下一个 case / default / } 为止
        while(!GetCurrentTokenType().equals(TokenType.CASETK) &&
            !GetCurrentTokenType().equals(TokenType.DEFAULTTK) &&
            !GetCurrentTokenType().equals(TokenType.RBRACE)){
            AddNode(new Stmt());
        }
    }
}
