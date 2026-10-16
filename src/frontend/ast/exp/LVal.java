package frontend.ast.exp;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.SyntaxType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;
import midend.symbol.FuncSymbol;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;

public class LVal extends ComputeExp {
    // LVal -> Ident [ '[' Exp ']' ]
    public LVal() {
        super(SyntaxType.LVAL_EXP);
    }
    
    @Override
    public void Parse() {
        // Ident
        AddNode(new Ident());
        // [ Exp ]
        if (GetCurrentTokenType().equals(TokenType.LBRACK)) {
            AddNode(new TokenNode());//[
            AddNode(new Exp());
            if (GetCurrentTokenType().equals(TokenType.RBRACK)) {
                AddNode(new TokenNode());//]
            } else {
                AddMissRBrackError();
            }
        }
    }
    
    @Override
    public void Visit() {
        super.Visit();

        Ident ident = GetIdent();
        String name = ident.GetTokenString();
        Symbol symbol = SymbolManger.GetSymbol(name);
        if (symbol == null) {
            SymbolManger.CheckNameUndefineError(name, ident.GetTokenLine());
            return;
        }

        if (components.size() <= 1) {
            if(symbol instanceof FuncSymbol){
                ErrorRecorder.AddError(new Error(ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,ident.GetTokenLine()));
            }
            return;
        }

        SymbolType symbolType = symbol.GetSymbolType();
        boolean isArray = switch (symbolType) {
            case INT_ARRAY, CONST_INT_ARRAY, STATIC_INT_ARRAY,
                 CHAR_ARRAY, CONST_CHAR_ARRAY, STATIC_CHAR_ARRAY -> true;
            default -> false;
        };
        if (!isArray) {
            ErrorRecorder.AddError(new Error(
                ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                ident.GetTokenLine()
            ));
            return;
        }

        Exp exp = (Exp) components.get(2);
        String expType = exp.GetExpType();
        if (!"INT".equals(expType) && !"ERROR".equals(expType)) {
            ErrorRecorder.AddError(new Error(
                ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                ident.GetTokenLine()
            ));
        }
    }
    
    @Override
    public void Compute() {
    }
    
    public Ident GetIdent() {
        return (Ident)this.components.get(0);
    }
    
    @Override
    public String GetExpType() {
        Symbol symbol = SymbolManger.GetSymbol(
            ((Ident) components.get(0)).GetTokenString()
        );
        if (symbol == null) {
            return "ERROR";
        }

        boolean isArrayElement = components.size() > 1;
        switch (symbol.GetSymbolType()) {
            case INT, CONST_INT, STATIC_INT:
                return isArrayElement ? "ERROR" : "INT";
            case CHAR, CONST_CHAR, STATIC_CHAR:
                return isArrayElement ? "ERROR" : "CHAR";
            case INT_ARRAY, CONST_INT_ARRAY, STATIC_INT_ARRAY:
                return isArrayElement ? "INT" : "INT_ARRAY";
            case CHAR_ARRAY, CONST_CHAR_ARRAY, STATIC_CHAR_ARRAY:
                return isArrayElement ? "CHAR" : "CHAR_ARRAY";
            default:
                return "ERROR";
        }
    }
}
