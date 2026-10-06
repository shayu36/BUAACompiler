package frontend.lexer;

public enum TokenType {
    // 标识符
    IDENFR,
    // 数字常量
    INTCON,
    // 字符串常量
    STRCON,
    // 字符常量
    CHARCON,
    
    // main
    MAINTK,
    
    //static
    STATICTK,
    // const
    CONSTTK,
    // int
    INTTK,
    // char
    CHARTK,
    // void
    VOIDTK,
    
    // break
    BREAKTK,
    // continue
    CONTINUETK,
    
    // if
    IFTK,
    // else
    ELSETK,
    // switch
    SWITCHTK,
    //case
    CASETK,
    //default
    DEFAULTTK,
    // while
    WHILETK,

    // !
    NOT,
    // &&
    AND,
    // ||
    OR,
    
    // printf()
    PRINTFTK,
    // return
    RETURNTK,
    
    // +
    PLUS,
    // -
    MINU,
    // *
    MULT,
    // /
    DIV,
    // %
    MOD,
    
    // <
    LSS,
    // <=
    LEQ,
    // >
    GRE,
    // >=
    GEQ,
    // ==
    EQL,
    // !=
    NEQ,
    // =
    ASSIGN,
    
    //:
    COLON,
    // ;
    SEMICN,
    // ,
    COMMA,
    
    // (
    LPARENT,
    // )
    RPARENT,
    // [
    LBRACK,
    // ]
    RBRACK,
    // {
    LBRACE,
    // }
    RBRACE,
    
    // EOF
    EOF,
    // error
    ERROR;
    
    public static TokenType GetTokenType(String identifier) {
        return switch (identifier) {
            case "<=" -> TokenType.LEQ;
            case ">=" -> TokenType.GEQ;
            case "==" -> TokenType.EQL;
            case "!=" -> TokenType.NEQ;
            case "&&" -> TokenType.AND;
            case "||" -> TokenType.OR;
            case "main" -> TokenType.MAINTK;
            case "static" -> TokenType.STATICTK;
            case "const" -> TokenType.CONSTTK;
            case "int" -> TokenType.INTTK;
            case "char" -> TokenType.CHARTK;
            case "void" -> TokenType.VOIDTK;
            case "break" -> TokenType.BREAKTK;
            case "continue" -> TokenType.CONTINUETK;
            case "if" -> TokenType.IFTK;
            case "else" -> TokenType.ELSETK;
            case "switch" -> TokenType.SWITCHTK;
            case "case" -> TokenType.CASETK;
            case "default" -> TokenType.DEFAULTTK;
            case "while" -> TokenType.WHILETK;
            case "printf" -> TokenType.PRINTFTK;
            case "return" -> TokenType.RETURNTK;
            default -> TokenType.IDENFR;
        };
    }
    
    public static TokenType GetTokenType(char character) {
        return switch (character) {
            case '+' -> TokenType.PLUS;
            case '-' -> TokenType.MINU;
            case '*' -> TokenType.MULT;
            case '%' -> TokenType.MOD;
            case ':' -> TokenType.COLON;
            case ';' -> TokenType.SEMICN;
            case ',' -> TokenType.COMMA;
            case '(' -> TokenType.LPARENT;
            case ')' -> TokenType.RPARENT;
            case '[' -> TokenType.LBRACK;
            case ']' -> TokenType.RBRACK;
            case '{' -> TokenType.LBRACE;
            case '}' -> TokenType.RBRACE;
            case '&' -> TokenType.AND;
            case '|' -> TokenType.OR;
            case '<' -> TokenType.LSS;
            case '>' -> TokenType.GRE;
            case '=' -> TokenType.ASSIGN;
            case '!' -> TokenType.NOT;
            default -> TokenType.ERROR;
        };
    }
}
