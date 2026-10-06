package frontend.lexer;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;

public class Lexer {
    private PushbackInputStream reader;
    private ArrayList<Token> tokenList;
    private char currentChar;
    private int lineNumber;
    
    public Lexer(PushbackInputStream reader) throws IOException {
        this.reader = reader;
        this.tokenList = new ArrayList<Token>();
        Read();
        lineNumber = 1;
    }
    
    public void GenerateTokenList() throws IOException {
        Token token = getToken();
        while (!token.GetTokenType().equals(TokenType.EOF)) {
            tokenList.add(token);
            token = getToken();
        }
    }
    
    private void Read() throws IOException {
        this.currentChar = (char) reader.read();
    }
    
    private void UnRead() throws IOException {
        this.reader.unread(this.currentChar);
    }
    
    public ArrayList<Token> GetTokenList() {
        return this.tokenList;
    }
    
    private Token getToken() throws IOException {
        StringBuilder string = new StringBuilder();
        
        // 跳过空白字符
        SkipBlank();
        
        // 处理结束情况
        if (this.IsEof()) {
            return new Token(TokenType.EOF, "EOF", this.lineNumber);
        }
        // 数字常量
        else if (this.IsDigit()) {
            return this.LexerDigit(string);
        }
        // 字符串常量
        else if (this.IsStringConst()) {
            return this.LexerStringConst(string);
        }
        // 字符常量
        else if (this.IsCharacterConst()) {
            return this.LexerCharacterConst(string);
        }
        // 标识符
        else if (this.IsIdentifier()) {
            return this.LexerIdentifier(string);
        }
        // 处理注释
        else if (this.IsAnnotation()) {
            return this.LexerAnnotation(string);
        }
        // 处理一般的符号
        else {
            return this.LexerOp(string);
        }
    }
    
    public void SkipBlank() throws IOException {
        while (this.IsBlank()) {
            if (this.IsNewLine()) {
                lineNumber++;
            }
            Read();
        }
    }
    
    public Token LexerDigit(StringBuilder sb) throws IOException {
        while (Character.isDigit(currentChar)) {
            sb.append(currentChar);
            Read();
        }
        return new Token(TokenType.INTCON, sb.toString(), this.lineNumber);
    }
    
    public Token LexerStringConst(StringBuilder sb) throws IOException {
        sb.append(currentChar);
        Read();
        while (currentChar != '"') {
            sb.append(currentChar);
            Read();
        }
        sb.append(currentChar);
        Read();
        return new Token(TokenType.STRCON, sb.toString(), this.lineNumber);
    }
    
    public Token LexerCharacterConst(StringBuilder sb) throws IOException {
        sb.append(currentChar);
        Read();
        if (currentChar == '\\') {
            sb.append(currentChar);
            Read();
        }
        sb.append(currentChar);
        Read();
        sb.append(currentChar);//'
        Read();
        return new Token(TokenType.CHARCON, sb.toString(), this.lineNumber);
    }
    
    public Token LexerIdentifier(StringBuilder sb) throws IOException {
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
            sb.append(currentChar);
            Read();
        }
        return new Token(TokenType.GetTokenType(sb.toString()), sb.toString(), this.lineNumber);
    }
    
    private Token LexerAnnotation(StringBuilder string) throws IOException {
        string.append(this.currentChar);
        this.Read();
        
        // 单行注释
        if (this.currentChar == '/') {
            this.Read();
            while (!this.IsNewLine() && !this.IsEof()) {
                this.Read();
            }
            this.lineNumber++;
            
            // 利用递归返回下一个token
            this.Read();
            return this.getToken();
        }
        // 多行注释
        else if (this.currentChar == '*') {
            this.Read();
            while (true) {
                while (this.currentChar != '*') {
                    if (this.IsEof()) {
                        return new Token(TokenType.EOF, "EOF", this.lineNumber);
                    }
                    
                    if (this.IsNewLine()) {
                        this.lineNumber++;
                    }
                    
                    this.Read();
                }
                
                this.Read();
                if (this.currentChar == '/') {
                    break;
                }
            }
            
            this.Read();
            return this.getToken();
        }
        // 一般情况
        else {
            return new Token(TokenType.DIV, string.toString(), this.lineNumber);
        }
    }
    
    //有些一定单独出现 有些可能双出 有些必须双出
    private Token LexerOp(StringBuilder string) throws IOException {
        return switch (this.currentChar) {
            case '+', '-', '*', '%', ';', ',', '(', ')', '[', ']', '{', '}', ':' ->
                this.LexerSingleOp(string);
            case '<', '>', '!', '=' -> this.LexerTwiceEqual(string);
            case '&', '|' -> this.LexerOpTwiceWithError(string);
            default -> new Token(TokenType.ERROR, string.toString(), this.lineNumber);
        };
    }
    
    private Token LexerSingleOp(StringBuilder string) throws IOException {
        char character = this.currentChar;
        string.append(character);
        this.Read();
        return new Token(TokenType.GetTokenType(character),
            string.toString(), this.lineNumber);
    }
    
    private Token LexerTwiceEqual(StringBuilder string) throws IOException {
        char character = this.currentChar;
        string.append(character);
        this.Read();
        
        if (this.currentChar == '=') {
            string.append(this.currentChar);
            this.Read();
            return new Token(TokenType.GetTokenType(string.toString()),
                string.toString(), this.lineNumber);
        } else {
            return new Token(TokenType.GetTokenType(character),
                string.toString(), this.lineNumber);
        }
    }
    
    private Token LexerOpTwiceWithError(StringBuilder string) throws IOException {
        char character = this.currentChar;
        string.append(character);
        this.Read();
        if (this.currentChar != character) {
            ErrorRecorder.AddError(new Error(ErrorType.ILLEGAL_SYMBOL, this.lineNumber));
        } else {
            this.Read();
            string.append(character);
        }
        
        return new Token(TokenType.GetTokenType(character), string.toString(), this.lineNumber);
    }
    
    private boolean IsBlank() throws IOException {
        return this.currentChar == ' ' ||
            this.currentChar == '\t' ||
            this.IsNewLine();
    }
    
    private boolean IsNewLine() throws IOException {
        if (this.currentChar == '\r') {
            this.Read();
        }
        return this.currentChar == '\n';
    }
    
    public boolean IsEof() {
        return this.currentChar == '\uFFFF';
    }
    
    public boolean IsDigit() {
        return Character.isDigit(currentChar);
    }
    
    public boolean IsStringConst() {
        return currentChar == '"';
    }
    
    public boolean IsCharacterConst() {
        return currentChar == '\'';
    }
    
    public boolean IsIdentifier() {
        return Character.isLetter(currentChar) || currentChar == '_';
    }
    
    public boolean IsAnnotation() {
        return currentChar == '/';
    }
}
