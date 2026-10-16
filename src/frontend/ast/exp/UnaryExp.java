package frontend.ast.exp;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.func.FuncRealParamS;
import frontend.ast.token.BType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;
import midend.symbol.FuncSymbol;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;
import midend.symbol.ValueSymbol;

import java.util.ArrayList;

public class UnaryExp extends ComputeExp {
    // UnaryExp -> PrimaryExp | Ident '(' [ FuncRParams ] ')' | UnaryOp UnaryExp
    //            | '(' BType ')' UnaryExp
    public UnaryExp() {
        super(SyntaxType.UNARY_EXP);
    }
    
    @Override
    public String GetExpType() {
        Node first=components.get(0);
        if(first instanceof PrimaryExp) {
            return  ((PrimaryExp)first).GetExpType();
        }
        else if(first instanceof Ident ident) {
            Symbol symbol = SymbolManger.GetSymbol(ident.GetTokenString());
            if (symbol == null) {
                return "ERROR";
            }
            return switch (symbol.GetSymbolType()) {
                case INT_FUNC -> "INT";
                case CHAR_FUNC -> "CHAR";
                case VOID_FUNC -> "VOID_FUNC";
                default -> "ERROR";
            };
        }
        else if(first instanceof UnaryOp unaryOp) {
            if ("!".equals(unaryOp.GetSimpleName())) {
                String type = ((ComputeExp) components.get(1)).GetExpType();
                if ("ERROR".equals(type)) {
                    return "ERROR";
                }
                return "INT".equals(type) || "CHAR".equals(type) ? "INT" : type;
            }
            return ((ComputeExp) components.get(1)).GetExpType();
        }
        else{
            BType bType = (BType) components.get(1);
            if ("int".equals(bType.GetTokenString())) {
                return "INT";
            }
            return "CHAR";
        }
    }
    
    @Override
    public void Parse() {
        if (GetCurrentTokenType().equals(TokenType.IDENFR) &&
            Peek(1).GetTokenType().equals(TokenType.LPARENT)) {
            // 函数调用：Ident '(' [FuncRParams] ')'
            // Ident
            AddNode(new Ident());
            // (
            AddNode(new TokenNode());
            // FuncRParams
            if (IsExpStmt()) {
                AddNode(new FuncRealParamS());
            }
            // )
            if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
                AddNode(new TokenNode());
            } else {
                AddMissRParentError();
            }
        } else if (GetCurrentTokenType().equals(TokenType.LPARENT) &&
            (Peek(1).GetTokenType().equals(TokenType.INTTK) ||
                Peek(1).GetTokenType().equals(TokenType.CHARTK))) {
            //'(' BType ')' UnaryExp
            AddNode(new TokenNode());//(
            AddNode(new BType());
            if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
                AddNode(new TokenNode());//)
            } else {
                AddMissRParentError();
            }
            AddNode(new UnaryExp());
        } else if (GetCurrentTokenType().equals(TokenType.PLUS) ||
            GetCurrentTokenType().equals(TokenType.MINU) ||
            GetCurrentTokenType().equals(TokenType.NOT)) {
            // 一元运算符：UnaryOp UnaryExp
            // UnaryOp
            AddNode(new UnaryOp());
            // UnaryExp
            AddNode(new UnaryExp());
        } else {
            // PrimaryExp
            AddNode(new PrimaryExp());
        }
    }
    
    @Override
    public void Visit() {
        for (Node component : this.components) {
            component.Visit();
            
            this.CheckIdentUnDefinedError(component);
        }
    }
    
    private void CheckIdentUnDefinedError(Node component) {
        if (!(component instanceof Ident ident)) {
            return;
        }
        
        String identName = ident.GetTokenString();
        int line = ident.GetTokenLine();
        Symbol symbol = SymbolManger.GetSymbol(identName);
        
        if (symbol == null) {
            ErrorRecorder.AddError(new Error(ErrorType.NAME_UNDEFINED, line));
            return;
        }

        // UnaryExp 中出现 Ident 时表示函数调用。
        // 如果查到的是变量或常量，说明把 ValueSymbol 当成了函数使用。
        if (symbol instanceof ValueSymbol) {
            ErrorRecorder.AddError(new Error(
                ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                line
            ));
            return;
        }

        // 如果是函数，检查参数
        if (symbol instanceof FuncSymbol funcSymbol) {
            if(!funcSymbol.isIO()){
            this.CheckFuncParam(funcSymbol, line);}
        }
    }
    
    private void CheckFuncParam(FuncSymbol funcSymbol, int line) {
        FuncRealParamS funcRealParamS = this.GetFuncRealParaS();
        // 无参数情况
        if (funcRealParamS == null) {
            if (!funcSymbol.GetFormalParamList().isEmpty()) {
                ErrorRecorder.AddError(new Error(ErrorType.FUNC_PARAM_NUM_NOT_MATCH, line));
            }
        }
        // 有参数
        else {
            ArrayList<Exp> realParamList = funcRealParamS.GetRealParamList();
            int realParamCount = realParamList.size();
            
            ArrayList<Symbol> formalParamList = funcSymbol.GetFormalParamList();
            int formalParamCount = formalParamList.size();
            
            // 处理参数不匹配的问题
            if (realParamCount != formalParamCount) {
                ErrorRecorder.AddError(new Error(ErrorType.FUNC_PARAM_NUM_NOT_MATCH, line));
                return;
            }
            //处理类型不匹配问题
            CheckParamTypeFit(realParamList, formalParamList, line);
        }
    }
    
    public void CheckParamTypeFit(ArrayList<Exp> realParamList, ArrayList<Symbol> formalParamList, int line) {
        for (int i = 0; i < formalParamList.size(); i++) {
            SymbolType formalSymbolType = formalParamList.get(i).GetSymbolType();
            //常量和静态变量会被 LVal.GetExpType() 统一
            String realType = realParamList.get(i).GetExpType();
            boolean typeMatch = switch (formalSymbolType) {
                case INT -> "INT".equals(realType);
                case CHAR -> "CHAR".equals(realType);
                case INT_ARRAY -> "INT_ARRAY".equals(realType);
                case CHAR_ARRAY -> "CHAR_ARRAY".equals(realType);
                default -> false;
            };
            if (!typeMatch && !"ERROR".equals(realType)) {
                ErrorRecorder.AddError(new Error(
                    ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                    line
                ));
            }
        }
    }
    
    private FuncRealParamS GetFuncRealParaS() {
        for (Node component : this.components) {
            if (component instanceof FuncRealParamS funcRealParamS) {
                return funcRealParamS;
            }
        }
        return null;
    }
    
    @Override
    public void Compute() {
    }
    
}
