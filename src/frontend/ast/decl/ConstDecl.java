package frontend.ast.decl;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.BType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class ConstDecl extends Node {
    // ConstDecl -> 'const' BType ConstDef { ',' ConstDef } ';'
    public ConstDecl() {
        super(SyntaxType.CONST_DECL);
    }
    
    @Override
    public void Parse() {
        AddNode(new TokenNode());//const
        AddNode(new BType());
        AddNode(new ConstDef());
        while (GetCurrentTokenType().equals(TokenType.COMMA)) {
            AddNode(new TokenNode());//,
            AddNode(new ConstDef());
        }
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            AddNode(new TokenNode());//;
        } else {
            AddMissSemicnError();
        }
    }
    
    @Override
    public void Visit() {
        // int | char
        BType type = (BType) this.components.get(1);
        String typeString = type.GetTokenString();
        
        // 可能会有多个ConstDef
        for (Node component : this.components) {
            // 标注类型
            if (component instanceof ConstDef constDef) {
                constDef.setTypeString(typeString);
            }
            component.Visit();
        }
    }
}
