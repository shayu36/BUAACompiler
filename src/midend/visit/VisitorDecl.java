package midend.visit;

import frontend.ast.decl.ConstDecl;
import frontend.ast.decl.ConstDef;
import frontend.ast.decl.Decl;
import frontend.ast.decl.VarDecl;
import frontend.ast.decl.VarDef;
import frontend.ast.exp.Exp;
import frontend.ast.value.InitVal;
import midend.llvm.IrBuilder;
import midend.llvm.constant.IrConstant;
import midend.llvm.constant.IrConstantArray;
import midend.llvm.constant.IrConstantChar;
import midend.llvm.constant.IrConstantInt;
import midend.llvm.instr.AllocateInstr;
import midend.llvm.instr.ExtendInstr;
import midend.llvm.instr.GepInstr;
import midend.llvm.instr.StoreInstr;
import midend.llvm.instr.TruncInstr;
import midend.llvm.type.IrArrayType;
import midend.llvm.type.IrBaseType;
import midend.llvm.type.IrPointerType;
import midend.llvm.type.IrType;
import midend.llvm.value.IrGlobalValue;
import midend.llvm.value.IrValue;
import midend.symbol.Symbol;
import midend.symbol.SymbolManger;
import midend.symbol.SymbolType;
import midend.symbol.ValueSymbol;

import java.util.ArrayList;

public class VisitorDecl {
    public static void VisitDecl(Decl decl) {
        if (decl.IsConstDecl()) {
            VisitConstDecl((ConstDecl) decl.GetDecl());
        } else {
            VisitVarDecl((VarDecl) decl.GetDecl());
        }
    }
    
    private static void VisitConstDecl(ConstDecl constDecl) {
        ArrayList<ConstDef> constDefs = constDecl.GetConstDef();
        for (ConstDef constDef : constDefs) {
            VisitConstDef(constDef);
        }
    }
    
    private static void VisitConstDef(ConstDef constDef) {
        ValueSymbol valueSymbol = (ValueSymbol) SymbolManger.GetSymbol(constDef.GetIdent().GetTokenString());
        //全局的声明
        if (SymbolManger.IsGlobal()) {
            IrValue irGlobalValue = IrBuilder.GetNewIrGlobalValue(
                new IrPointerType(GetSymbolIrType(valueSymbol)),
                GetValueConstant(valueSymbol)
            );
            valueSymbol.setIrValue(irGlobalValue);
        }
        //局部的声明
        else {
            AllocateInstr allocateInstr = new AllocateInstr(GetSymbolIrType(valueSymbol), false);
            IrBuilder.AddInstrToEntryBlock(allocateInstr);
            valueSymbol.setIrValue(allocateInstr);
            //简单变量
            if (valueSymbol.getDimension() == 0) {
                new StoreInstr(GetValueConstant(valueSymbol), allocateInstr);
            }
            //数组
            else {
                ArrayList<Integer> initValueList = valueSymbol.GetInitValueList();
                //不足的部分补 0
                for (int i = initValueList.size(); i < valueSymbol.GetTotalDepth(); i++) {
                    initValueList.add(0);
                }
                for (int i = 0; i < initValueList.size(); i++) {
                    GepInstr gepInstr = new GepInstr(allocateInstr, new IrConstantInt(i));
                    //按数组元素类型选常量：i8 → IrConstantChar，否则 IrConstantInt
                    IrArrayType irArrayType = (IrArrayType) GetSymbolIrType(valueSymbol);
                    StoreInstr storeInstr = irArrayType.GetElementType().IsInt8Type() ?
                        new StoreInstr(new IrConstantChar(initValueList.get(i)), gepInstr) :
                        new StoreInstr(new IrConstantInt(initValueList.get(i)), gepInstr);
                }
            }
        }
    }
    
    private static void VisitVarDecl(VarDecl varDecl) {
        ArrayList<VarDef> varDefs = varDecl.GetVarDef();
        for (VarDef varDef : varDefs) {
            VisitVarDef(varDef);
        }
    }
    
    private static void VisitVarDef(VarDef varDef) {
        ValueSymbol valueSymbol = (ValueSymbol) SymbolManger.GetSymbol(varDef.GetIdent().GetTokenString());
        //全局的声明 + static 局部变量（static 跨调用持久 → 同样走全局变量，天然只初始化一次）
        if (SymbolManger.IsGlobal() || IsStaticSymbol(valueSymbol)) {
            IrValue irGlobalValue = IrBuilder.GetNewIrGlobalValue(
                new IrPointerType(GetSymbolIrType(valueSymbol)),
                GetValueConstant(valueSymbol)
            );
            valueSymbol.setIrValue(irGlobalValue);
        }
        //局部的声明
        else {
            AllocateInstr allocateInstr = new AllocateInstr(GetSymbolIrType(valueSymbol), false);
            IrBuilder.AddInstrToEntryBlock(allocateInstr);
            valueSymbol.setIrValue(allocateInstr);
            //简单变量
            if (valueSymbol.getDimension() == 0) {
                if (varDef.HaveInitVal()) {
                    IrValue initValue = VisitorExp.VisitExp(varDef.GetInitVal().GetExpList().get(0));
                    new StoreInstr(MatchStoreValueType(initValue, allocateInstr.GetTargetType()), allocateInstr);
                }
            }
            //数组
            else {
                if (varDef.HaveInitVal()) {
                    InitVal initVal = varDef.GetInitVal();
                    boolean isChar = ((IrArrayType) GetSymbolIrType(valueSymbol))
                        .GetElementType().IsInt8Type();
                    // 字符串初值：逐字节 store，不足补 0
                    if (initVal.HaveStringConst()) {
                        // token 带首尾引号（"AB"），剥掉引号再逐字符处理
                        String token = initVal.GetStringConst().GetTokenString();
                        String string = token.substring(1, token.length() - 1);
                        int index = 0;
                        for (int i = 0; i < string.length(); i++) {
                            char ch = string.charAt(i);
                            int byteValue;
                            if (ch == '\\' && i < string.length() - 1 &&
                                string.charAt(i + 1) == 'n') {
                                byteValue = '\n';
                                i++;
                            } else {
                                byteValue = ch;
                            }
                            // 数组长度小于字符串：只写前 N 字节（截断）
                            if (index < valueSymbol.GetTotalDepth()) {
                                GepInstr gepInstr = new GepInstr(allocateInstr, new IrConstantInt(index));
                                new StoreInstr(isChar ? new IrConstantChar(byteValue) :
                                    new IrConstantInt(byteValue), gepInstr);
                                index++;
                            }
                        }
                        for (; index < valueSymbol.GetTotalDepth(); index++) {
                            GepInstr gepInstr = new GepInstr(allocateInstr, new IrConstantInt(index));
                            new StoreInstr(isChar ? new IrConstantChar(0) :
                                new IrConstantInt(0), gepInstr);
                        }
                    }
                    // 列表/单表达式初值
                    else {
                        ArrayList<Exp> expList = initVal.GetExpList();
                        for (int i = 0; i < valueSymbol.GetTotalDepth(); i++) {
                            GepInstr gepInstr = new GepInstr(allocateInstr, new IrConstantInt(i));
                            if (i < expList.size()) {
                                IrValue elementValue = VisitorExp.VisitExp(expList.get(i));
                                new StoreInstr(MatchStoreValueType(elementValue,
                                    ((IrArrayType) GetSymbolIrType(valueSymbol)).GetElementType()), gepInstr);
                            } else {
                                new StoreInstr(isChar ? new IrConstantChar(0) :
                                    new IrConstantInt(0), gepInstr);
                            }
                        }
                    }
                }
                // 无初值：不 store（局部变量无初值 = 未定义）
            }
        }
    }
    
    // 右值与格子类型不一致时转换：i32 进 i8 格子 → trunc；i8 进 i32 格子 → zext
    private static IrValue MatchStoreValueType(IrValue value, IrType targetType) {
        if (value.GetIrType().IsInt32Type() && targetType.IsInt8Type()) {
            return new TruncInstr(value, IrBaseType.INT8);
        }
        if (value.GetIrType().IsInt8Type() && targetType.IsInt32Type()) {
            return new ExtendInstr(value, IrBaseType.INT32);
        }
        return value;
    }

    private static boolean IsStaticSymbol(ValueSymbol valueSymbol) {
        return switch (valueSymbol.GetSymbolType()) {
            case STATIC_INT, STATIC_CHAR, STATIC_INT_ARRAY, STATIC_CHAR_ARRAY -> true;
            default -> false;
        };
    }

    private static IrType GetSymbolIrType(ValueSymbol symbol) {
        return switch (symbol.GetSymbolType()) {
            case CHAR, CONST_CHAR, STATIC_CHAR -> IrBaseType.INT8;
            case INT, CONST_INT, STATIC_INT -> IrBaseType.INT32;
            case CHAR_ARRAY, CONST_CHAR_ARRAY, STATIC_CHAR_ARRAY ->
                new IrArrayType(symbol.GetTotalDepth(), IrBaseType.INT8);
            case INT_ARRAY, CONST_INT_ARRAY, STATIC_INT_ARRAY ->
                new IrArrayType(symbol.GetTotalDepth(), IrBaseType.INT32);
            default -> throw new RuntimeException("now fit value type for global value");
        };
    }
    
    private static IrConstant GetValueConstant(ValueSymbol valueSymbol) {
        ArrayList<Integer> initValueList = valueSymbol.GetInitValueList();
        // 非数组类型
        if (valueSymbol.getDimension() == 0) {
            if (initValueList.isEmpty()) {
                initValueList.add(0);
            }
            int initValue = initValueList.get(0);
            return GetSymbolIrType(valueSymbol).equals(IrBaseType.INT8) ?
                new IrConstantChar(initValue) : new IrConstantInt(initValue);
        }
        // 数组类型
        else {
            IrArrayType irArrayType = (IrArrayType) GetSymbolIrType(valueSymbol);
            int totalDepth = valueSymbol.GetTotalDepth();
            IrType irType = irArrayType.GetElementType();
            ArrayList<IrConstant> constantList = new ArrayList<>();
            if (irType.IsInt8Type()) {
                for (Integer num : initValueList) {
                    constantList.add(new IrConstantChar(num));
                }
                for (int i = initValueList.size(); i < totalDepth; i++) {
                    constantList.add(new IrConstantChar(0));
                }
            } else {
                for (Integer num : initValueList) {
                    constantList.add(new IrConstantInt(num));
                }
                for (int i = initValueList.size(); i < totalDepth; i++) {
                    constantList.add(new IrConstantInt(0));
                }
            }
            
            return new IrConstantArray(valueSymbol.GetTotalDepth(), irType,
                valueSymbol.GetSymbolName(), constantList);
        }
    }
    
    
}
