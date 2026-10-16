package frontend.ast.value;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.token.CharConst;
import frontend.ast.token.IntConst;
import frontend.lexer.TokenType;

public class Number extends Node {
    // Number -> IntConst | CharConst
    public Number() {
        super(SyntaxType.NUMBER);
    }
    
    @Override
    public void Parse() {
        // IntConst
        if (GetCurrentTokenType().equals(TokenType.INTCON)) {
            AddNode(new IntConst());
        }
        // CharConst
        else {
            AddNode(new CharConst());
        }
    }
    
    public int GetCaseNumber() {
        String value = this.components.get(0).GetSimpleName();
        if (value.length() >= 2 && value.charAt(0) == '\'' &&
            value.charAt(value.length() - 1) == '\'') {
            String charValue = value.substring(1, value.length() - 1);
            return switch (charValue) {
                case "\\n" -> '\n';
                case "\\t" -> '\t';
                case "\\r" -> '\r';
                case "\\0" -> '\0';
                case "\\\\" -> '\\';
                case "\\'" -> '\'';
                default -> charValue.charAt(0);
            };
        }
        return Integer.parseInt(value);
    }
    
    public String GetExpType() {
        return components.get(0) instanceof IntConst ? "INT" : "CHAR";
    }
}
