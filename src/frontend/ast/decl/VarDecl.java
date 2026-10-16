package frontend.ast.decl;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.BType;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class VarDecl extends Node {
    // VarDecl -> [ 'static' ] BType VarDef { ',' VarDef } ';'
    public VarDecl() {
        super(SyntaxType.VAR_DECL);
    }
    @Override
    public void Parse() {
        //[static]
        if (GetCurrentTokenType().equals(TokenType.STATICTK)) {
            this.AddNode(new TokenNode());
        }
        this.AddNode(new BType());
        this.AddNode(new VarDef());
        while (GetCurrentTokenType().equals(TokenType.COMMA)) {
            this.AddNode(new TokenNode());//,
            this.AddNode(new VarDef());
        }
        
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            this.AddNode(new TokenNode());//;
        } else {
            AddMissSemicnError();
        }
    }
    
    @Override
    public void Visit() {
        // int | char
        int typeIndex = this.components.get(0) instanceof BType ? 0 : 1;
        BType type = (BType) this.components.get(typeIndex);
        String typeString = type.GetTokenString();
        boolean isStatic = (typeIndex == 1);
        
        // 可能会有多个 VarDef
        for (Node component : this.components) {
            // 为每个 VarDef 标注基本类型
            if (component instanceof VarDef varDef) {
                varDef.setTypeString(typeString);
                varDef.setIsStatic(isStatic);
            }
            component.Visit();
        }
    }
}
