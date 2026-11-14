package midend.symbol;

import error.Error;
import error.ErrorRecorder;
import error.ErrorType;
import midend.llvm.type.IrBaseType;
import midend.llvm.value.IrFunction;

import java.util.ArrayList;

public class SymbolManger {
    private static SymbolTable rootSymbolTable;
    private static SymbolTable currentSymbolTable;
    private static int depth;
    private static int whileDepth;
    private static int switchDepth;
    private static String funcReturnType = "";
    private static Boolean inWhile;
    private static Boolean inSwitch;
    
    // 初始化全局作用域
    public static void Init() {
        depth = 1;
        whileDepth = 0;
        switchDepth = 0;
        rootSymbolTable = new SymbolTable(1, null);
        currentSymbolTable = rootSymbolTable;
        inWhile = false;
        inSwitch = false;
    }
    
    // 进入一个新的子作用域
    public static void CreateSonSymbolTable() {
        SymbolTable sonSymbolTable = new SymbolTable(++depth, currentSymbolTable);
        currentSymbolTable.addSonTable(sonSymbolTable);
        currentSymbolTable = sonSymbolTable;
    }
    
    public static void GoToFatherTable() {
        currentSymbolTable = currentSymbolTable.getFatherTable();
    }
    
    public static void GoToSonSymbolTable() {
        currentSymbolTable = currentSymbolTable.GetNextSonTable();
    }
    
    public static void GoBackToRootSymbolTable() {
        currentSymbolTable = rootSymbolTable;
    }
    
    public static boolean IsGlobal() {
        return currentSymbolTable == rootSymbolTable;
    }
    
    public static void AddSymbol(Symbol symbol, int line) {
        currentSymbolTable.addSymbol(symbol, line);
    }
    
    public static Symbol GetSymbol(String name) {
        Symbol symbol = GetSymbolInCurrentScope(name);
        if (symbol == null) {
            symbol = GetSymbolFromFather(name);
        }
        if (symbol != null) {
            return symbol;
        }
        return GetIOFunction(name);
    }
    
    private static Symbol GetIOFunction(String name) {
        return switch (name) {
            case "get_int" -> {
                FuncSymbol symbol = new FuncSymbol(name, SymbolType.INT_FUNC, new ArrayList<>());
                // 只声明不定义：new IrFunction 不会注册进模块（注册是 GetNewFunctionIr 的事）
                symbol.setIrValue(new IrFunction("@" + name, IrBaseType.INT32));
                yield symbol;
            }
            case "get_char" -> {
                FuncSymbol symbol = new FuncSymbol(name, SymbolType.CHAR_FUNC, new ArrayList<>());
                symbol.setIrValue(new IrFunction("@" + name, IrBaseType.INT8));
                yield symbol;
            }
            case "get_string", "put_int", "put_char",
                 "put_string", "put_str" -> {
                FuncSymbol symbol = new FuncSymbol(name, SymbolType.VOID_FUNC, new ArrayList<>());
                symbol.setIrValue(new IrFunction("@" + name, IrBaseType.VOID));
                yield symbol;
            }
            default -> null;
        };
    }
    
    public static void CheckNameUndefineError(String name, int line) {
        if (GetSymbol(name) == null) {
            ErrorRecorder.AddError(new Error(ErrorType.NAME_UNDEFINED, line));
        }
    }
    
    public static Symbol GetSymbolInCurrentScope(String name) {
        return currentSymbolTable.getSymbolInCurrentScope(name);
    }
    
    public static Symbol GetSymbolFromFather(String name) {
        SymbolTable table = currentSymbolTable.getFatherTable();
        
        while (table != null) {
            Symbol symbol = table.getSymbolInCurrentScope(name);
            
            if (symbol != null) {
                return symbol;
            }
            
            table = table.getFatherTable();
        }
        
        return null;
    }
    
    public static SymbolTable GetrootSymbolTable() {
        return rootSymbolTable;
    }
    
    public static void EnterFunc(String type) {
        funcReturnType = type;
    }
    
    public static void LeaveFunc() {
        funcReturnType = "";
    }
    
    public static String GetFuncType() {
        return funcReturnType;
    }
    
    public static void EnterWhileBlock() {
        whileDepth++;
    }
    
    public static void LeaveWhileBlock() {
        whileDepth--;
    }
    
    public static void EnterSwitchBlock() {
        switchDepth++;
    }
    
    public static void LeaveSwitchBlock() {
        switchDepth--;
    }
    
    public static boolean GetIsinWhile() {
        return whileDepth > 0;
    }
    
    public static boolean GetIsinSwitch() {
        return switchDepth > 0;
    }
}
