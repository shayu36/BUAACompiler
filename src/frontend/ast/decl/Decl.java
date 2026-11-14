package frontend.ast.decl;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.lexer.TokenType;

public class Decl extends Node {
    // Decl -> ConstDecl | VarDecl
    public Decl() {
        super(SyntaxType.DECL);
        this.printSelf=false;
    }

    @Override
    public void Parse() {
        if(GetCurrentTokenType().equals(TokenType.CONSTTK)){
            AddNode(new ConstDecl());
        }
        else {
            AddNode(new VarDecl());
        }
    }
    
    public Boolean IsConstDecl(){
        return this.components.get(0) instanceof ConstDecl;
    }

    public Node GetDecl() {
        return this.components.get(0);
    }
}
