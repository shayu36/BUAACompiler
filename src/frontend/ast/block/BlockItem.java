package frontend.ast.block;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.decl.Decl;
import frontend.ast.stmt.Stmt;
import frontend.lexer.TokenType;

public class BlockItem extends Node {
    public BlockItem() {
        super(SyntaxType.BLOCK_ITEM);
        this.printSelf = false;
    }
    
    @Override
    public void Parse() {
        if (GetCurrentTokenType().equals(TokenType.CONSTTK) ||
            GetCurrentTokenType().equals(TokenType.INTTK) ||
            GetCurrentTokenType().equals(TokenType.CHARTK) ||
            GetCurrentTokenType().equals(TokenType.STATICTK)) {
            AddNode(new Decl());
        } else{
            AddNode(new Stmt());
        }
    }
}
