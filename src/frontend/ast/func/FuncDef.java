package frontend.ast.func;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.block.Block;
import frontend.ast.token.FuncType;
import frontend.ast.token.Ident;
import frontend.ast.token.TokenNode;
import frontend.lexer.TokenType;
import midend.symbol.FuncSymbol;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;

import java.util.ArrayList;

public class FuncDef extends Node {
    // FuncDef -> FuncType Ident '(' [ FuncFParams ] ')' Block
    public FuncDef() {
        super(SyntaxType.FUNC_DEF);
    }
    
    @Override
    public void Visit() {
        FuncType type = (FuncType) this.components.get(0);
        String typeString = type.GetTokenString();
        Ident ident = (Ident) this.components.get(1);
        FuncSymbol symbol = new FuncSymbol(
            ident.GetTokenString(),
            SymbolType.GetFuncType(typeString),
            new ArrayList<>()
        );
        int line=ident.GetTokenLine();
        SymbolManger.AddSymbol(symbol,line);
        SymbolManger.EnterFunc(typeString);
        SymbolManger.CreateSonSymbolTable();
        for(Node component: components) {
            component.Visit();
            if(component instanceof FuncFormalParamS formalParamS) {
                symbol.SetFormalParamList(formalParamS.GetFormalParamList());
            }
        }
        this.CheckReturnError(typeString, this.GetBlock());
        SymbolManger.LeaveFunc();
        SymbolManger.GoToFatherTable();
    }

    private void CheckReturnError(String type, Block block) {
        if (!type.equals("void") && block.LastIsNotReturnStmt()) {
            ErrorRecorder.AddError(
                new Error(ErrorType.MISS_RETURN, block.GetLastLine())
            );
        }
    }

    public Block GetBlock() {
        for (Node component : this.components) {
            if (component instanceof Block block) {
                return block;
            }
        }
        return null;
    }
    
    @Override
    public void Parse() {
        AddNode(new FuncType());
        AddNode(new Ident());
        AddNode(new TokenNode());//(
        // FuncFParams
        if (GetCurrentTokenType().equals(TokenType.INTTK) ||
            GetCurrentTokenType().equals(TokenType.CHARTK) ||
            GetCurrentTokenType().equals(TokenType.STATICTK)
        ) {
            this.AddNode(new FuncFormalParamS());
        }
        if (GetCurrentTokenType().equals(TokenType.RPARENT)) {
            AddNode(new TokenNode());//)
        } else {
            AddMissRParentError();
        }
        AddNode(new Block());
    }
}
