package frontend.ast.stmt;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.block.Block;
import frontend.ast.exp.Cond;
import frontend.ast.exp.Exp;
import frontend.ast.exp.LVal;
import frontend.ast.token.StringConst;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Stmt extends Node {
    private final Map<Integer, Integer> caseNumberList = new HashMap<>();
    // Stmt -> LVal '=' Exp ';'
    //      | [ Exp ] ';'
    //      | Block
    //      | 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
    //      | 'while' '(' Cond ')' Stmt
    //      | 'switch' '(' Exp ')' '{' { CaseStmt } '}'
    //      | 'break' ';'
    //      | 'continue' ';'
    //      | 'return' [ Exp ] ';'
    //      | 'printf' '(' StringConst { ',' Exp } ')' ';'
    private StmtType stmtType;
    private int defaultCount = 0;
    
    public Stmt() {
        super(SyntaxType.STMT);
    }
    
    @Override
    public void Visit() {
        if (stmtType == StmtType.BlockStmt) {
            SymbolManger.CreateSonSymbolTable();
            for (Node component : components) {
                component.Visit();
            }
            SymbolManger.GoToFatherTable();
        } else if (stmtType == StmtType.AssignStmt) {
            LVal lval = (LVal) components.get(0);
            Exp exp = (Exp) components.get(2);
            lval.Visit();
            exp.Visit();
            Symbol symbol = SymbolManger.GetSymbol(
                lval.GetIdent().GetTokenString()
            );
            if (symbol == null) {
                return;
            }
            SymbolType lvaltype = symbol.GetSymbolType();
            String exptype = exp.GetExpType();
            int line = lval.GetIdent().GetTokenLine();
            if (lvaltype.IsConst()) {
                ErrorRecorder.AddError(new Error(
                    ErrorType.CHANGE_CONST_VALUE,
                    line
                ));
            } else if ("VOID_FUNC".equals(exptype) ||
                (!lval.GetExpType().equals(exptype) && !"ERROR".equals(exptype))) {
                ErrorRecorder.AddError(new Error(
                    ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                    line
                ));
            }
        } else if (stmtType == StmtType.PrintStmt) {
            String formatString = ((StringConst) (components.get(2))).GetTokenString();
            ArrayList<String> typeList = new ArrayList<>();
            for (int i = 0; i + 1 < formatString.length(); i++) {
                if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 'd') {
                    typeList.add("INT");
                    i++;
                } else if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 'c') {
                    typeList.add("CHAR");
                    i++;
                } else if (formatString.charAt(i) == '%' && formatString.charAt(i + 1) == 's') {
                    typeList.add("CHAR_ARRAY");
                    i++;
                }
            }
            
            int realParameterCount = 0;
            for (Node component : components) {
                if (component instanceof Exp exp) {
                    exp.Visit();
                    String expType = exp.GetExpType();
                    if (realParameterCount < typeList.size() &&
                        !"ERROR".equals(expType) &&
                        !expType.equals(typeList.get(realParameterCount))) {
                        TokenNode printToken = (TokenNode) components.get(0);
                        ErrorRecorder.AddError(new Error(
                            ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                            printToken.GetTokenLine()
                        ));
                    }
                    realParameterCount++;
                }
            }
            if (realParameterCount != typeList.size()) {
                TokenNode printToken = (TokenNode) components.get(0);
                ErrorRecorder.AddError(new Error(
                    ErrorType.PRINTF_NOT_MATCH,
                    printToken.GetTokenLine()
                ));
            }
            
        } else if (stmtType == StmtType.IfStmt) {
            for (Node component : components) {
                component.Visit();
            }
            CheckConditionType(
                (Cond) components.get(2),
                ((TokenNode) components.get(0)).GetTokenLine()
            );
        } else if (stmtType == StmtType.WhileStmt) {
            SymbolManger.EnterWhileBlock();
            for (Node component : components) {
                component.Visit();
            }
            CheckConditionType(
                (Cond) components.get(2),
                ((TokenNode) components.get(0)).GetTokenLine()
            );
            SymbolManger.LeaveWhileBlock();
        } else if (stmtType == StmtType.BreakStmt) {
            if (!SymbolManger.GetIsinWhile() && !SymbolManger.GetIsinSwitch()) {
                TokenNode tokenNode = (TokenNode) components.get(0);//break
                ErrorRecorder.AddError(new Error(
                    ErrorType.BREAK_OR_CONTINUE_IN_NOT_LOOP,
                    tokenNode.GetTokenLine()
                ));
            }
        } else if (stmtType == StmtType.ContinueStmt) {
            if (!SymbolManger.GetIsinWhile()) {
                TokenNode tokenNode = (TokenNode) components.get(0);
                ErrorRecorder.AddError(new Error(
                    ErrorType.BREAK_OR_CONTINUE_IN_NOT_LOOP,
                    tokenNode.GetTokenLine()
                ));
            }
        } else if (stmtType == StmtType.ReturnStmt) {
            for (Node component : components) {
                component.Visit();
            }
            TokenNode returnToken = (TokenNode) components.get(0);
            if (components.get(1) instanceof Exp returnExp) {
                String functionType = SymbolManger.GetFuncType();
                if (functionType.equals("void")) {
                    ErrorRecorder.AddError(new Error(
                        ErrorType.RETURN_NOT_MATCH,
                        returnToken.GetTokenLine()
                    ));
                } else {
                    String expectedType = functionType.equals("int") ? "INT" : "CHAR";
                    String actualType = returnExp.GetExpType();
                    if (!expectedType.equals(actualType) && !"ERROR".equals(actualType)) {
                        ErrorRecorder.AddError(new Error(
                            ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                            returnToken.GetTokenLine()
                        ));
                    }
                }
            } else if (!SymbolManger.GetFuncType().equals("void")) {
                ErrorRecorder.AddError(new Error(
                    ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                    returnToken.GetTokenLine()
                ));
            }
        } else if (stmtType == StmtType.SwitchStmt) {
            SymbolManger.EnterSwitchBlock();
            String switchType = ((Exp) components.get(2)).GetExpType();
            for (Node component : components) {
                if (component instanceof CaseStmt caseStmt) {
                    if (caseStmt.GetTypeString().equals("case")) {
                        //switch 是 INT，case 必须是 INT
                        //switch 是 CHAR，case 必须是 CHAR
                        //switch 是 ERROR，不重复报告 e
                        //switch 是数组或 VOID_FUNC，报告 e
                        boolean switchIsBasic = "INT".equals(switchType) ||
                            "CHAR".equals(switchType);
                        boolean caseTypeMismatch = switchIsBasic &&
                            (("INT".equals(switchType)) != caseStmt.NumberisInt());
                        if ((!switchIsBasic && !"ERROR".equals(switchType)) ||
                            caseTypeMismatch) {
                            ErrorRecorder.AddError(new Error(
                                ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                                caseStmt.GetLine()
                            ));
                        }
                        int caseNumber = caseStmt.GetCaseNumber();
                        if (caseNumberList.containsKey(caseNumber)) {
                            ErrorRecorder.AddError(new Error(
                                ErrorType.CASE_DEFAULT_REDEFINE, caseStmt.GetLine()
                            ));
                        } else {
                            caseNumberList.put(caseNumber, caseStmt.GetLine());
                        }
                    } else {
                        if (defaultCount == 1) {
                            ErrorRecorder.AddError(new Error(
                                ErrorType.CASE_DEFAULT_REDEFINE, caseStmt.GetLine()
                            ));
                        } else {
                            defaultCount++;
                        }
                    }
                    caseStmt.Visit();
                } else {
                    component.Visit();
                }
            }
            SymbolManger.LeaveSwitchBlock();
        } else if(stmtType==StmtType.ExpStmt){
            Exp exp = (Exp) components.get(0);
            exp.Visit();
            exp.GetExpType();
        }else {
            for (Node component : components) {
                component.Visit();
            }
        }
    }

    private void CheckConditionType(Cond cond, int line) {
        String conditionType = cond.GetExpType();
        if (!"INT".equals(conditionType) && !"ERROR".equals(conditionType)) {
            ErrorRecorder.AddError(new Error(
                ErrorType.FUNC_PARAM_TYPE_NOT_MATCH,
                line
            ));
        }
    }
    
    @Override
    public void Parse() {
        switch (GetCurrentTokenType()) {
            case LBRACE -> this.ParseBlockStmt();
            case IFTK -> this.ParseIfStmt();
            case WHILETK -> this.ParseWhileStmt();
            case SWITCHTK -> this.ParseSwitchStmt();
            case BREAKTK -> this.ParseBreakStmt();
            case CONTINUETK -> this.ParseContinueStmt();
            case RETURNTK -> this.ParseReturnStmt();
            case PRINTFTK -> this.ParsePrintStmt();
            case SEMICN -> this.AddNode(new TokenNode());//空表达式语句
            default -> this.ParseAssignOrExpStmt();// 赋值语句or表达式语句（需回溯区分）
        }
    }
    
    // Block
    private void ParseBlockStmt() {
        this.stmtType = StmtType.BlockStmt;
        AddNode(new Block());
    }
    
    private void ParseIfStmt() {
        // 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
        this.stmtType = StmtType.IfStmt;
        AddNode(new TokenNode());//if
        AddNode(new TokenNode());//(
        AddNode(new Cond());//Cond
        if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
            AddNode(new TokenNode());//)
        } else {
            AddMissRParentError();
        }
        AddNode(new Stmt());
        if (GetCurrentTokenType().equals(TokenType.ELSETK)) {
            AddNode(new TokenNode());//else
            AddNode(new Stmt());
        }
    }
    
    private void ParseWhileStmt() {
        // 'while' '(' Cond ')' Stmt
        this.stmtType = StmtType.WhileStmt;
        AddNode(new TokenNode());//while
        AddNode(new TokenNode());//(
        AddNode(new Cond());
        if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
            AddNode(new TokenNode());//)
        } else {
            AddMissRParentError();
        }
        AddNode(new Stmt());
    }
    
    private void ParseSwitchStmt() {
        // 'switch' '(' Exp ')' '{' { CaseStmt } '}'
        this.stmtType = StmtType.SwitchStmt;
        AddNode(new TokenNode());//switch
        AddNode(new TokenNode());//(
        AddNode(new Exp());
        if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
            AddNode(new TokenNode());//)
        } else {
            AddMissRParentError();
        }
        AddNode(new TokenNode());//{
        while (!GetCurrentTokenType().equals(TokenType.RBRACE)) {
            AddNode(new CaseStmt());
        }
        AddNode(new TokenNode());//}
    }
    
    // 'break' ';'
    private void ParseBreakStmt() {
        this.stmtType = StmtType.BreakStmt;
        AddNode(new TokenNode());//break
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            AddNode(new TokenNode());//;
        } else {
            AddMissSemicnError();
        }
    }
    
    // 'continue' ';'
    private void ParseContinueStmt() {
        this.stmtType = StmtType.ContinueStmt;
        AddNode(new TokenNode());//continue
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            AddNode(new TokenNode());//;
        } else {
            AddMissSemicnError();
        }
    }
    
    private void ParseReturnStmt() {
        // 'return' [Exp] ';'
        this.stmtType = StmtType.ReturnStmt;
        TokenNode returnToken = new TokenNode();
        AddNode(returnToken);//return
        if (IsExpStmt()) {
            AddNode(new Exp());
        }
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            AddNode(new TokenNode());//;
        } else {
            AddMissSemicnError();
        }
    }
    
    private void ParsePrintStmt() {
        // 'printf' '(' StringConst { ',' Exp } ')' ';'
        this.stmtType = StmtType.PrintStmt;
        AddNode(new TokenNode());//printf
        AddNode(new TokenNode());//(
        AddNode(new StringConst());
        while (GetCurrentTokenType().equals(TokenType.COMMA)) {
            AddNode(new TokenNode());//,
            AddNode(new Exp());
        }
        if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
            AddNode(new TokenNode());//)
        } else {
            AddMissRParentError();
        }
        if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
            AddNode(new TokenNode());//;
        } else {
            AddMissSemicnError();
        }
    }
    
    private void ParseAssignOrExpStmt() {
        SetBackPoint();
        ErrorRecorder.SetStopRecordError();
        Node node = new Exp();//随便建一个 关键在于移动readPoint指针
        node.Parse();//先当作一个表达式来解析 看看是不是移动到=了
        if (GetCurrentTokenType().equals(TokenType.ASSIGN)) {
            this.stmtType = StmtType.AssignStmt;
            //LVal '=' Exp ';'
            GoToBackPoint();
            ErrorRecorder.SetStartRecordError();
            AddNode(new LVal());
            AddNode(new TokenNode());//=
            AddNode(new Exp());
            if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
                AddNode(new TokenNode());//;
            } else {
                AddMissSemicnError();
            }
        } else {
            this.stmtType = StmtType.ExpStmt;
            //Exp ';'
            GoToBackPoint();
            ErrorRecorder.SetStartRecordError();
            AddNode(new Exp());
            if (GetCurrentTokenType().equals(TokenType.SEMICN)) {
                AddNode(new TokenNode());//;
            } else {
                AddMissSemicnError();
            }
        }
    }
    
    public StmtType GetStmtType() {
        return this.stmtType;
    }
    
    
    public enum StmtType {
        BlockStmt,
        IfStmt,
        WhileStmt,
        SwitchStmt,
        AssignStmt,
        ReturnStmt,
        ExpStmt,
        BreakStmt,
        ContinueStmt,
        PrintStmt,
    }
}
