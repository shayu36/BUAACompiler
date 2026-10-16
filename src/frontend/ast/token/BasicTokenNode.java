package frontend.ast.token;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.lexer.Token;

public class BasicTokenNode extends Node {
    protected Token token;
    
    public BasicTokenNode(SyntaxType syntaxType) {
        super(syntaxType);
    }
    
    public String GetTokenString() {
        return this.token.GetStringValue();
    }
    
    public int GetTokenLine() {
        return  this.token.GetLineNumber();
    }

    @Override
    public String GetSimpleName() {
        return this.token.GetStringValue();
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
