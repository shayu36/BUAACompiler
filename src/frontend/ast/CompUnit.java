package frontend.ast;

import frontend.ast.decl.Decl;
import frontend.lexer.TokenType;
import frontend.ast.func.FuncDef;
import frontend.ast.func.MainFuncDef;

import java.util.ArrayList;

public class CompUnit extends Node {
    // CompUnit -> { Decl } { FuncDef } MainFuncDef
    public CompUnit() {
        super(SyntaxType.COMP_UNIT);
    }
    
    @Override
    public void Parse() {
        while (GetCurrentToken() != null) {
            //主函数
            if (Peek(1).GetTokenType().equals(TokenType.MAINTK)) {
                this.AddNode(new MainFuncDef());
            }
            //函数定义
            else if (Peek(2).GetTokenType().equals(TokenType.LPARENT)) {
                this.AddNode(new FuncDef());
            }
            //声明
            else if (GetCurrentTokenType().equals(TokenType.CONSTTK) ||
                GetCurrentTokenType().equals(TokenType.INTTK) ||
                GetCurrentTokenType().equals(TokenType.CHARTK) ||
                GetCurrentTokenType().equals(TokenType.STATICTK)) {
                this.AddNode(new Decl());
            } else {
                break;
            }
        }
    }

    public ArrayList<Decl> GetDecls() {
        ArrayList<Decl> decls = new ArrayList<>();
        for (Node component : this.components) {
            if (component instanceof Decl decl) {
                decls.add(decl);
            }
        }
        return decls;
    }

    public ArrayList<FuncDef> GetFuncDefs() {
        ArrayList<FuncDef> funcDefs = new ArrayList<>();
        for (Node component : this.components) {
            if (component instanceof FuncDef funcDef) {
                funcDefs.add(funcDef);
            }
        }
        return funcDefs;
    }

    public MainFuncDef GetMainFuncDef() {
        for (Node component : this.components) {
            if (component instanceof MainFuncDef mainFuncDef) {
                return mainFuncDef;
            }
        }
        return null;
    }
}
