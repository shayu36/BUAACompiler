package frontend.ast.token;

import frontend.ast.SyntaxType;

public class FuncType extends BasicTokenNode {
    // FuncType -> 'void' | 'int' | 'char'
    public FuncType() {
        super(SyntaxType.FUNC_TYPE);
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.token.GetTokenType() + " " + this.token.GetStringValue()+"\n");
        sb.append(("<" + this.syntaxType + ">"));
        return sb.toString();
    }
}
