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
import frontend.ast.token.Ident;
import frontend.ast.token.StringConst;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;

public class Stmt extends Node {
    public Stmt() {
        super(SyntaxType.STMT);
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
        AddNode(new Block());
    }

    // 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
    private void ParseIfStmt() {
        AddNode(new TokenNode());//if
        AddNode(new TokenNode());//(
        AddNode(new Cond());//Cond
        if(GetCurrentTokenType().equals(TokenType.RPARENT)){
            AddNode(new TokenNode());//)
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
        }
        AddNode(new Stmt());
        if(GetCurrentTokenType().equals(TokenType.ELSETK)){
            AddNode(new TokenNode());//else
            AddNode(new Stmt());
        }
    }

    // 'while' '(' Cond ')' Stmt
    private void ParseWhileStmt() {
        AddNode(new TokenNode());//while
        AddNode(new TokenNode());//(
        AddNode(new Cond());
        if(GetCurrentTokenType().equals(TokenType.RPARENT)){
            AddNode(new TokenNode());//)
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
        }
        AddNode(new Stmt());
    }

    // 'switch' '(' Exp ')' '{' { CaseStmt } '}'
    private void ParseSwitchStmt() {
        AddNode(new TokenNode());//switch
        AddNode(new TokenNode());//(
        AddNode(new Exp());
        if(GetCurrentTokenType().equals(TokenType.RPARENT)){
            AddNode(new TokenNode());//)
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
        }
        AddNode(new TokenNode());//{
        while(!GetCurrentTokenType().equals(TokenType.RBRACE)){
            AddNode(new CaseStmt());
        }
        AddNode(new TokenNode());//}
    }

    // 'break' ';'
    private void ParseBreakStmt() {
        AddNode(new TokenNode());//break
        if(GetCurrentTokenType().equals(TokenType.SEMICN)){
            AddNode(new TokenNode());//;
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
        }
    }

    // 'continue' ';'
    private void ParseContinueStmt() {
        AddNode(new TokenNode());//continue
        if(GetCurrentTokenType().equals(TokenType.SEMICN)){
            AddNode(new TokenNode());//;
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
        }
    }

    // 'return' [Exp] ';'
    private void ParseReturnStmt() {
        AddNode(new TokenNode());//return
        if(!GetCurrentTokenType().equals(TokenType.SEMICN)){
            AddNode(new Exp());
        }
        if(GetCurrentTokenType().equals(TokenType.SEMICN)){
            AddNode(new TokenNode());//;
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
        }
    }

    // 'printf' '(' StringConst { ',' Exp } ')' ';'
    private void ParsePrintStmt() {
        AddNode(new TokenNode());//printf
        AddNode(new TokenNode());//(
        AddNode(new StringConst());
        while(GetCurrentTokenType().equals(TokenType.COMMA)){
            AddNode(new TokenNode());//,
            AddNode(new Exp());
        }
        if(GetCurrentTokenType().equals(TokenType.RPARENT)){
            AddNode(new TokenNode());//)
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_RPARENT, Peek(-1).GetLineNumber()));
        }
        if(GetCurrentTokenType().equals(TokenType.SEMICN)){
            AddNode(new TokenNode());//;
        } else {
            ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
        }
    }

    private void ParseAssignOrExpStmt(){
        SetBackPoint();
        Node node=new Exp();//随便建一个 关键在于移动readPoint指针
        node.Parse();//先当作一个表达式来解析 看看是不是移动到=了
        //LVal '=' Exp ';'
        if(GetCurrentTokenType().equals(TokenType.ASSIGN)){
            GoToBackPoint();
            AddNode(new LVal());
            AddNode(new TokenNode());//=
            AddNode(new Exp());
            if(GetCurrentTokenType().equals(TokenType.SEMICN)){
                AddNode(new TokenNode());//;
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
            }
        }
        else{
            //Exp ';'
            GoToBackPoint();
            AddNode(new Exp());
            if(GetCurrentTokenType().equals(TokenType.SEMICN)){
                AddNode(new TokenNode());//;
            } else {
                ErrorRecorder.AddError(new Error(ErrorType.MISS_SEMICN, Peek(-1).GetLineNumber()));
            }
        }
    }
}
