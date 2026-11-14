package midend.visit;

import frontend.ast.Node;
import frontend.ast.exp.Cond;
import frontend.ast.exp.Exp;
import frontend.ast.exp.PrimaryExp;
import frontend.ast.exp.UnaryExp;
import frontend.ast.exp.UnaryOp;
import frontend.ast.exp.recursion.AddExp;
import frontend.ast.exp.recursion.EqExp;
import frontend.ast.exp.recursion.LAndExp;
import frontend.ast.exp.recursion.LOrExp;
import frontend.ast.exp.recursion.MulExp;
import frontend.ast.exp.recursion.RelExp;
import frontend.ast.token.Ident;
import frontend.ast.token.IntConst;
import frontend.ast.token.TokenNode;
import frontend.ast.value.Number;
import midend.llvm.IrBuilder;
import midend.llvm.constant.IrConstantChar;
import midend.llvm.constant.IrConstantInt;
import midend.llvm.instr.*;
import midend.llvm.type.IrBaseType;
import midend.llvm.type.IrType;
import midend.llvm.value.IrBasicBlock;
import midend.llvm.value.IrFunction;
import midend.llvm.value.IrValue;
import midend.symbol.FuncSymbol;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;

import java.util.ArrayList;

public class VisitorExp {
    public static IrValue VisitExp(Exp exp) {
        return VisitAddExp(exp.GetAddExp());
    }
    
    public static IrValue VisitAddExp(AddExp addExp) {
        ArrayList<Node> nodeList = addExp.GetNodeList();
        MulExp mulExp1 = (MulExp) nodeList.get(0);
        IrValue irValue1 = VisitMulExp(mulExp1);
        IrValue irValue2 = null;
        
        int index = 1;
        while (index < nodeList.size()) {
            TokenNode op = (TokenNode) nodeList.get(index++);
            irValue2 = VisitMulExp((MulExp) nodeList.get(index++));
            IrValue result = new AluInstr(op.GetSimpleName(), irValue1, irValue2);
            irValue1 = result;
        }
        return irValue1;
    }
    
    public static IrValue VisitMulExp(MulExp mulExp) {
        ArrayList<Node> nodeList = mulExp.GetNodeList();
        UnaryExp unaryExp1 = (UnaryExp) nodeList.get(0);
        IrValue irValue1 = VisitUnaryExp(unaryExp1);
        IrValue irValue2 = null;
        
        int index = 1;
        while (index < nodeList.size()) {
            TokenNode op = (TokenNode) nodeList.get(index++);
            irValue2 = VisitUnaryExp((UnaryExp) nodeList.get(index++));
            IrValue result = new AluInstr(op.GetSimpleName(), irValue1, irValue2);
            irValue1 = result;
        }
        return irValue1;
    }
    
    public static IrValue VisitUnaryExp(UnaryExp unaryExp) {
        //PrimaryExp
        if (unaryExp.IsPrimaryType()) {
            return VisitPrimaryExp(unaryExp.GetPrimaryExp());
        }
        //函数调用
        else if (unaryExp.IsIdentType()) {
            FuncSymbol funcSymbol = (FuncSymbol) SymbolManger.GetSymbol(unaryExp.GetIdent().GetTokenString());
            
            IrFunction irFunction = (IrFunction) funcSymbol.GetIrValue();
            ArrayList<Exp> realParamList = unaryExp.GetRealParamList();
            ArrayList<IrValue> paramList = new ArrayList<>();
            for (Exp para : realParamList) {
                paramList.add(VisitExp(para));
            }
            return new CallInstr(irFunction, paramList);
        }
        //UnaryOp UnaryExp
        else if (unaryExp.IsUnaryOpType()) {
            switch (unaryExp.GetUnaryOp().GetSimpleName()) {
                case "+" -> {
                    return VisitUnaryExp(unaryExp.GetUnaryExp());
                }
                case "-" -> {
                    IrValue subValue = VisitUnaryExp(unaryExp.GetUnaryExp());
                    IrValue zero = subValue.GetIrType().IsInt8Type() ?
                        new IrConstantChar(0) : new IrConstantInt(0);
                    return new AluInstr("-", zero, subValue);
                }
                case "!" -> {
                    IrValue subValue = VisitUnaryExp(unaryExp.GetUnaryExp());
                    IrValue zero = subValue.GetIrType().IsInt8Type() ?
                        new IrConstantChar(0) : new IrConstantInt(0);
                    return new ExtendInstr(new CompareInstr("==", zero, subValue), IrBaseType.INT32);
                }
                default -> throw new RuntimeException("illegal unary op");
            }
        }
        //'(' BType ')' UnaryExp（显式类型转换）
        else {
            String target = unaryExp.GetBType().GetTokenString();
            String source = unaryExp.GetUnaryExp().GetExpType();
            IrValue subValue = VisitUnaryExp(unaryExp.GetUnaryExp());
            if (target.equals("int") && source.equals("CHAR")) {
                return new ExtendInstr(subValue, IrBaseType.INT32);
            } else if (target.equals("char") && source.equals("INT")) {
                return new TruncInstr(subValue, IrBaseType.INT8);
            } else {
                return subValue;
            }
        }
    }
    
    public static IrValue VisitPrimaryExp(PrimaryExp primaryExp) {
        //(Exp)
        if (primaryExp.IsExpType()) {
            return VisitExp(primaryExp.GetExp());
        }
        //LVal
        else if (primaryExp.IsLValType()) {
            return VisitorLVal.VisitLVal(primaryExp.GetLVal(), false);
        }
        //Number
        else {
            Number number = primaryExp.GetNumber();
            if (number.GetExpType().equals("INT")) {
                return new IrConstantInt(number.GetCaseNumber());
            } else {
                return new IrConstantChar(number.GetCaseNumber());
            }
        }
    }
    
    public static void VisitCond(Cond cond, IrBasicBlock trueBlock, IrBasicBlock falseBlock) {
        VisitLOrExp(cond.GetLOrExp(), trueBlock, falseBlock);
    }
    
    public static void VisitLOrExp(LOrExp lorExp, IrBasicBlock trueBlock, IrBasicBlock falseBlock) {
        ArrayList<LAndExp> landExpList = lorExp.GetLAndExpList();
        for (int i = 0; i < landExpList.size() - 1; i++) {
            IrBasicBlock nextOrBlock = IrBuilder.GetNewBasicBlockIr();
            VisitLAndExp(landExpList.get(i), trueBlock, nextOrBlock);
            // 进入下一个block
            //我没看懂 但还是先这样了
            IrBuilder.SetCurrentBasicBlock(nextOrBlock);
        }
        VisitLAndExp(landExpList.get(landExpList.size() - 1), trueBlock, falseBlock);
    }
    
    public static void VisitLAndExp(LAndExp landExp, IrBasicBlock trueBlock, IrBasicBlock falseBlock) {
        ArrayList<EqExp> eqExpList = landExp.GetEqExpList();
        for (int i = 0; i < eqExpList.size() - 1; i++) {
            IrBasicBlock nextOrBlock = IrBuilder.GetNewBasicBlockIr();
            IrValue irValue = VisitEqExp(eqExpList.get(i));
            // 逻辑运算结果是 i32，br 需要 i1：非零即真（trunc 只取最低位，偶数会错判）
            if (irValue.GetIrType().IsInt32Type()) {
                irValue = new CompareInstr("!=", irValue, new IrConstantInt(0));
            }
            new BranchInstr(irValue, nextOrBlock, falseBlock);
            IrBuilder.SetCurrentBasicBlock(nextOrBlock);
        }
        IrValue irValue = VisitEqExp(eqExpList.get(eqExpList.size() - 1));
        if (irValue.GetIrType().IsInt32Type()) {
            irValue = new CompareInstr("!=", irValue, new IrConstantInt(0));
        }
        new BranchInstr(irValue, trueBlock, falseBlock);
    }
    
    public static IrValue VisitEqExp(EqExp eqExp) {
        ArrayList<Node> nodeList = eqExp.GetNodeList();
        RelExp relExp1 = (RelExp) nodeList.get(0);
        IrValue irValue1 = VisitRelExp(relExp1);
        IrValue irValue2 = null;
        
        int index = 1;
        while (index < nodeList.size()) {
            TokenNode op = (TokenNode) nodeList.get(index++);
            irValue2 = VisitRelExp((RelExp) nodeList.get(index++));
            // 比较结果是 int 0/1：i1 升 i32，才能继续参与下一个比较
            IrValue result = new ExtendInstr(
                new CompareInstr(op.GetSimpleName(), irValue1, irValue2), IrBaseType.INT32);
            irValue1 = result;
        }
        return irValue1;
    }

    public static IrValue VisitRelExp(RelExp relExp) {
        ArrayList<Node> nodeList = relExp.GetNodeList();
        AddExp addExp1 = (AddExp) nodeList.get(0);
        IrValue irValue1 = VisitAddExp(addExp1);
        IrValue irValue2 = null;
        
        int index = 1;
        while (index < nodeList.size()) {
            TokenNode op = (TokenNode) nodeList.get(index++);
            irValue2 = VisitAddExp((AddExp) nodeList.get(index++));
            // 比较结果是 int 0/1：i1 升 i32，才能继续参与下一个比较
            IrValue result = new ExtendInstr(
                new CompareInstr(op.GetSimpleName(), irValue1, irValue2), IrBaseType.INT32);
            irValue1 = result;
        }
        return irValue1;
    }
}
