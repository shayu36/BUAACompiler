package frontend.ast.exp;

import frontend.ast.Node;
import frontend.ast.SyntaxType;

public abstract class ComputeExp extends Node {
    public ComputeExp(SyntaxType syntaxType) {
        super(syntaxType);
    }

    public abstract void Compute();
}
