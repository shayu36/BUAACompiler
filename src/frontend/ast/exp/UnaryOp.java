package frontend.ast.exp;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.TokenNode;

public class UnaryOp extends Node {
    // UnaryOp -> '+' | '-' | '!'
    public UnaryOp() {
        super(SyntaxType.UNARY_OP);
    }

    @Override
    public String GetSimpleName() {
        TokenNode tokenNode = (TokenNode) this.components.get(0);
        return tokenNode.GetSimpleName();
    }

    @Override
    public void Parse() {
        // + | - | !
        AddNode(new TokenNode());
    }
}
