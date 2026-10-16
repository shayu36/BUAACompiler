package frontend.ast.block;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class Block extends Node {
    // Block -> '{' { BlockItem } '}'
    public Block() {
        super(SyntaxType.BLOCK);
    }
    
    @Override
    public void Parse() {
        // {
        AddNode(new TokenNode());
        //  BlockItem
        while (!GetCurrentTokenType().equals(TokenType.RBRACE)) {
            AddNode(new BlockItem());
        }
        // }
        AddNode(new TokenNode());
    }
    
    public boolean LastIsNotReturnStmt(){
        if(components.size()==2) return true;
        BlockItem blockItem = (BlockItem)components.get(components.size()-2);
        return !blockItem.IsReturn();
    }

    public int GetLastLine() {
        TokenNode rightBrace = (TokenNode) components.get(components.size() - 1);
        return rightBrace.GetTokenLine();
    }
}
