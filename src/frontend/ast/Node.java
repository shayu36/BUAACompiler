package frontend.ast;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.token.TokenNode;
import frontend.lexer.Token;
import frontend.lexer.TokenStream;
import frontend.lexer.TokenType;
import utils.Setting;

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

    protected boolean IsExpStmt() {
        return GetCurrentTokenType().equals(TokenType.IDENFR) ||
            GetCurrentTokenType().equals(TokenType.INTCON) ||
            GetCurrentTokenType().equals(TokenType.CHARCON) ||
            GetCurrentTokenType().equals(TokenType.PLUS) ||
            GetCurrentTokenType().equals(TokenType.MINU) ||
            GetCurrentTokenType().equals(TokenType.NOT) ||
            GetCurrentTokenType().equals(TokenType.LPARENT);
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

    protected void AddMissSemicnError() {
        int line = Peek(-1).GetLineNumber();
        ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, line));
    }

    protected void AddMissRParentError() {
        int line = Peek(-1).GetLineNumber();
        ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, line));
    }

    protected void AddMissRBrackError() {
        int line = Peek(-1).GetLineNumber();
        ErrorRecorder.AddError(new Error(ErrorType.MISS_RBRACK, line));
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
