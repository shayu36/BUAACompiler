package frontend.ast.token;

import frontend.ast.SyntaxType;

public class FuncType extends BasicTokenNode {
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
