package frontend.ast;

import frontend.lexer.Token;
import frontend.lexer.TokenStream;
import frontend.lexer.TokenType;

import java.util.ArrayList;

public abstract class Node {
    private static TokenStream tokenStream;
    protected final SyntaxType syntaxType;
    protected final ArrayList<Node> components;
    protected boolean printSelf;
    
    public Node(SyntaxType syntaxType) {
        this.syntaxType = syntaxType;
        this.components = new ArrayList<Node>();
        this.printSelf = true;
    }
    
    public static void SetTokenStream(TokenStream tokenStream) {
        Node.tokenStream = tokenStream;
    }
    
    protected void AddNode(Node node) {
        node.Parse();
        this.components.add(node);
    }
    
    public abstract void Parse();
    
    public TokenType GetCurrentTokenType() {
        return Node.tokenStream.Peek(0).GetTokenType();
    }
    
    public Token GetCurrentToken() {
        return Node.tokenStream.Peek(0);
    }
    
    public void Read() {
        Node.tokenStream.Read();
    }
    
    protected Token Peek(int peekStep) {
        return tokenStream.Peek(peekStep);
    }
    
    protected void SetBackPoint() {
        tokenStream.SetBackPoint();
    }
    
    protected void GoToBackPoint() {
        tokenStream.GoToBackPoint();
    }
    
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Node node : this.components) {
            stringBuilder.append(node);
            stringBuilder.append("\n");
        }
        
        if (printSelf) {
            stringBuilder.append("<" + this.syntaxType + ">");
        } else {
            if (!stringBuilder.isEmpty() &&
                stringBuilder.charAt(stringBuilder.length() - 1) == '\n') {
                stringBuilder.setLength(stringBuilder.length() - 1);
            }
        }
        
        return stringBuilder.toString();
    }
    
}
