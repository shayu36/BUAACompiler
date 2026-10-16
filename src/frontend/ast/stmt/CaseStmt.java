package frontend.ast.stmt;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.ast.value.Number;
import frontend.lexer.TokenType;

public class CaseStmt extends Node {
    // CaseStmt -> 'case' Number ':' { Stmt } | 'default' ':' { Stmt }
    public CaseStmt() {
        super(SyntaxType.CASE_STMT);
    }
    
    @Override
    public void Parse() {
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

    public String GetTypeString() {
        return ((TokenNode) this.components.get(0)).GetTokenString();
    }

    public int GetCaseNumber() {
        return ((Number) this.components.get(1)).GetCaseNumber();
    }
    
    public boolean NumberisInt(){
        return "INT".equals(((Number) this.components.get(1)).GetExpType());
    }
    public int GetLine() {
        return ((TokenNode) this.components.get(0)).GetTokenLine();
    }
}
