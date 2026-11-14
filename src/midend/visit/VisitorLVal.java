package midend.visit;

import frontend.ast.exp.Exp;
import frontend.ast.exp.LVal;
import frontend.ast.token.Ident;
import midend.llvm.constant.IrConstantInt;
import midend.llvm.instr.GepInstr;
import midend.llvm.instr.LoadInstr;
import midend.llvm.type.IrPointerType;
import midend.llvm.value.IrValue;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;
import midend.symbol.ValueSymbol;

import java.util.ArrayList;

public class VisitorLVal {
    public static IrValue VisitLVal(LVal lval, boolean beAssigned) {
        return beAssigned ? VisitLValForAssign(lval) : VisitLValForValue(lval);
    }
    
    //返回地址
    public static IrValue VisitLValForAssign(LVal lval) {
        Ident ident = lval.GetIdent();
        ValueSymbol symbol = (ValueSymbol) SymbolManger.GetSymbol(ident.GetTokenString());
        int dimension = symbol.getDimension();
        if (dimension == 0) {
            return symbol.getIrValue();
        } else {
            IrValue pointer = symbol.getIrValue();
            return new GepInstr(pointer, VisitorExp.VisitExp(lval.GetExp()));
        }
    }
    
    //返回值
    public static IrValue VisitLValForValue(LVal lval) {
        Ident ident = lval.GetIdent();
        ValueSymbol symbol = (ValueSymbol) SymbolManger.GetSymbol(ident.GetTokenString());
        if (symbol.getIrValue() == null) {
            symbol = (ValueSymbol) SymbolManger.GetSymbolFromFather(ident.GetTokenString());
        }
        int dimension = symbol.getDimension();
        if (dimension == 0) {
            return new LoadInstr(symbol.getIrValue());
        } else {
            IrValue pointer = symbol.getIrValue();
            //没带参数 是指针
            if (lval.GetExp() == null) {
                return new GepInstr(pointer, new IrConstantInt(0));
            }
            //带参数 是a[i]
            else {
                IrValue addr = new GepInstr(pointer, VisitorExp.VisitExp(lval.GetExp()));
                return new LoadInstr(addr);
            }
        }
        
    }
}
