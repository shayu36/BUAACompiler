package frontend.ast.token;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.lexer.Token;

public class BasicTokenNode extends Node {
    protected Token token;
    
    public BasicTokenNode(SyntaxType syntaxType) {
        super(syntaxType);
    }
    
    @Override
    public void Parse() {
        this.token = GetCurrentToken();
        Read();
    }
    
    @Override
    public String toString() {
        return this.token.GetTokenType() + " " + this.token.GetStringValue();
    }
}
