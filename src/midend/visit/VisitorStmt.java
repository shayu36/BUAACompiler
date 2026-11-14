package midend.visit;

import frontend.ast.block.Block;
import frontend.ast.exp.Cond;
import frontend.ast.exp.Exp;
import frontend.ast.exp.LVal;
import frontend.ast.stmt.CaseStmt;
import frontend.ast.stmt.Stmt;
import midend.llvm.IrBuilder;
import midend.llvm.constant.IrConstantChar;
import midend.llvm.constant.IrConstantInt;
import midend.llvm.constant.IrConstantString;
import midend.llvm.instr.*;
import midend.llvm.instr.io.GetCharInstr;
import midend.llvm.instr.io.GetIntInstr;
import midend.llvm.instr.io.GetStringInstr;
import midend.llvm.instr.io.PrintCharInstr;
import midend.llvm.instr.io.PrintIntInstr;
import midend.llvm.instr.io.PrintStrInstr;
import midend.llvm.type.IrBaseType;
import midend.llvm.type.IrPointerType;
import midend.llvm.type.IrType;
import midend.llvm.value.IrBasicBlock;
import midend.llvm.value.IrLoop;
import midend.llvm.value.IrValue;
import midend.symbol.SymbolManger;

import java.util.ArrayList;

import static midend.llvm.IrBuilder.LoopStackPeek;
import static midend.llvm.IrBuilder.LoopStackPush;

public class VisitorStmt {
    public static void VisitStmt(Stmt stmt) {
        switch (stmt.GetStmtType()) {
            case BlockStmt -> VisitBlockStmt(stmt);
            case ExpStmt -> VisitExpStmt(stmt);
            case AssignStmt -> VisitAssignStmt(stmt);
            case PrintStmt -> VisitPrintStmt(stmt);
            case ReturnStmt -> VisitReturnStmt(stmt);
            case IfStmt -> VisitIfStmt(stmt);
            case WhileStmt -> VisitWhileStmt(stmt);
            case SwitchStmt -> VisitSwitchStmt(stmt);
            case BreakStmt -> VisitBreakStmt(stmt);
            case ContinueStmt -> VisitContinueStmt(stmt);
            default -> throw new RuntimeException("illegal stmt type");
        }
    }
    
    private static void VisitBlockStmt(Stmt stmt) {
        SymbolManger.GoToSonSymbolTable();
        VisitorBlock.VisitBlock(stmt.GetBlockStmtBlock());
        SymbolManger.GoToFatherTable();
    }
    
    private static void VisitExpStmt(Stmt stmt) {
        if (stmt.ExpStmtHaveExp()) {
            VisitorExp.VisitExp(stmt.GetExpStmtExp());
        }
    }
    
    private static void VisitAssignStmt(Stmt stmt) {
        IrValue addr = VisitorLVal.VisitLValForAssign(stmt.GetAssignStmtLVal());
        IrValue value = VisitorExp.VisitExp(stmt.GetAssignStmtExp());
        new StoreInstr(value, addr);
    }
    
    private static void VisitReturnStmt(Stmt stmt) {
        if (stmt.ReturnStmtHaveExp()) {
            new ReturnInstr(VisitorExp.VisitExp(stmt.GetReturnStmtExp()));
        } else {
            new ReturnInstr(null);
        }
    }
    
    private static void VisitIfStmt(Stmt stmt) {
        Cond cond = stmt.GetIfStmtCond();
        
        IrBasicBlock ifBlock = IrBuilder.GetNewBasicBlockIr();
        IrBasicBlock elseBlock = (stmt.IfStmtHaveElse()) ? IrBuilder.GetNewBasicBlockIr() : null;
        IrBasicBlock followBlock = IrBuilder.GetNewBasicBlockIr();
        if (stmt.IfStmtHaveElse()) {
            VisitorExp.VisitCond(cond, ifBlock, elseBlock);
        } else {
            VisitorExp.VisitCond(cond, ifBlock, followBlock);
        }
        IrBuilder.SetCurrentBasicBlock(ifBlock);
        VisitorStmt.VisitStmt(stmt.GetIfStmtIfStmt());
        if (!IrBuilder.GetCurrentBasicBlock().LastInstrIsTerminator()) {
            new JumpInstr(followBlock);
        }
        if (stmt.IfStmtHaveElse()) {
            IrBuilder.SetCurrentBasicBlock(elseBlock);
            VisitorStmt.VisitStmt(stmt.GetIfStmtElseStmt());
            if (!IrBuilder.GetCurrentBasicBlock().LastInstrIsTerminator()) {
                new JumpInstr(followBlock);
            }
        }
        IrBuilder.SetCurrentBasicBlock(followBlock);
    }
    
    private static void VisitWhileStmt(Stmt stmt) {
        Cond cond = stmt.GetWhileStmtCond();
        IrBasicBlock condBlock = IrBuilder.GetNewBasicBlockIr();
        IrBasicBlock whileBlock = IrBuilder.GetNewBasicBlockIr();
        IrBasicBlock followBlock = IrBuilder.GetNewBasicBlockIr();
        IrBuilder.LoopStackPush(new IrLoop(condBlock, whileBlock, followBlock));
        
        new JumpInstr(condBlock);
        
        IrBuilder.SetCurrentBasicBlock(condBlock);
        VisitorExp.VisitCond(cond, whileBlock, followBlock);
        IrBuilder.SetCurrentBasicBlock(whileBlock);
        VisitorStmt.VisitStmt(stmt.GetWhileStmtStmt());
        if (!IrBuilder.GetCurrentBasicBlock().LastInstrIsTerminator()) {
            new JumpInstr(condBlock);
        }
        IrBuilder.SetCurrentBasicBlock(followBlock);
        IrBuilder.LoopStackPop();
    }
    
    
    private static void VisitSwitchStmt(Stmt stmt) {
        ArrayList<CaseStmt> caseList = stmt.GetCaseList();
        CaseStmt defaultStmt = stmt.GetDefaultStmt();
        ArrayList<IrBasicBlock> cmps = new ArrayList<>();
        for (int i = 0; i < caseList.size() - 1; i++) {
            cmps.add(IrBuilder.GetNewBasicBlockIr());
        }
        ArrayList<IrBasicBlock> bodys = new ArrayList<>();
        for (int i = 0; i < caseList.size(); i++) {
            bodys.add(IrBuilder.GetNewBasicBlockIr());
        }
        IrBasicBlock defaultBlock = defaultStmt != null ? IrBuilder.GetNewBasicBlockIr() : null;
        IrBasicBlock followBlock = IrBuilder.GetNewBasicBlockIr();
        IrBuilder.LoopStackPush(new IrLoop(null, null, followBlock));
        
        IrValue expValue = VisitorExp.VisitExp(stmt.GetSwitchStmtExp());
        
        CompareInstr compareInstr = new CompareInstr("==", expValue,
            expValue.GetIrType().IsInt8Type() ?
                new IrConstantChar(caseList.get(0).GetCaseNumber()) :
                new IrConstantInt(caseList.get(0).GetCaseNumber()));
        
        new BranchInstr(compareInstr, bodys.get(0),
            caseList.size() > 1 ? cmps.get(0) : (defaultBlock != null ? defaultBlock : followBlock));
        
        for (int i = 1; i < caseList.size(); i++) {
            IrBuilder.SetCurrentBasicBlock(cmps.get(i - 1));
            compareInstr = new CompareInstr("==", expValue,
                expValue.GetIrType().IsInt8Type() ?
                    new IrConstantChar(caseList.get(i).GetCaseNumber()) :
                    new IrConstantInt(caseList.get(i).GetCaseNumber()));
            new BranchInstr(compareInstr, bodys.get(i),
                i < caseList.size() - 1 ? cmps.get(i) :
                    (defaultBlock != null ? defaultBlock : followBlock));
        }
        // 填每个 case 体
        for (int i = 0; i < caseList.size(); i++) {
            IrBuilder.SetCurrentBasicBlock(bodys.get(i));
            for (Stmt caseBodyStmt : caseList.get(i).GetStmtList()) {
                VisitStmt(caseBodyStmt);
            }
            // 体不以 terminator 收尾 → 显式 fall-through 到下一块
            if (!IrBuilder.GetCurrentBasicBlock().LastInstrIsTerminator()) {
                new JumpInstr(i < caseList.size() - 1 ? bodys.get(i + 1) :
                    (defaultBlock != null ? defaultBlock : followBlock));
            }
        }
        // 填 default 体
        if (defaultBlock != null) {
            IrBuilder.SetCurrentBasicBlock(defaultBlock);
            for (Stmt defaultBodyStmt : defaultStmt.GetStmtList()) {
                VisitStmt(defaultBodyStmt);
            }
            // default 体同样：不收尾就显式落入 follow
            if (!IrBuilder.GetCurrentBasicBlock().LastInstrIsTerminator()) {
                new JumpInstr(followBlock);
            }
        }
        // 收尾
        IrBuilder.SetCurrentBasicBlock(followBlock);
        IrBuilder.LoopStackPop();
    }
    
    private static void VisitBreakStmt(Stmt stmt) {
        new JumpInstr(IrBuilder.LoopStackPeek().GetFollowBlock());
    }
    
    private static void VisitContinueStmt(Stmt stmt) {
        new JumpInstr(IrBuilder.GetCondBlockFromLoopStack());
    }
    
    private static void VisitPrintStmt(Stmt stmt) {
        String formatString = stmt.GetPrintStmtStringConst().GetTokenString();
        ArrayList<Exp> expList = stmt.GetPrintStmtExpList();
        // 先翻译所有实参
        ArrayList<IrValue> argList = new ArrayList<>();
        for (Exp exp : expList) {
            argList.add(VisitorExp.VisitExp(exp));
        }
        
        int expIndex = 0;
        StringBuilder segment = new StringBuilder();
        // 格式串含引号：从 1 遍历到 length-2
        for (int i = 1; i < formatString.length() - 1; i++) {
            char ch = formatString.charAt(i);
            if (ch == '%') {
                // 落地累积的普通字符段
                if (segment.length() > 0) {
                    IrConstantString irConstantString =
                        IrBuilder.GetNewIrConstantString(segment.toString());
                    new PrintStrInstr(new GepInstr(irConstantString, new IrConstantInt(0)));
                    segment.setLength(0);
                }
                i++;
                char spec = formatString.charAt(i);
                IrValue arg = argList.get(expIndex++);
                switch (spec) {
                    case 'd' -> new PrintIntInstr(arg);
                    case 'c' -> new PrintCharInstr(arg);
                    case 's' -> new PrintStrInstr(arg);
                }
            } else if (ch == '\\') {
                // \n 转义：保留原始两字符，交给 IrConstantString 转成 \0A
                segment.append("\\n");
                i++;
            } else {
                segment.append(ch);
            }
        }
        // 剩余普通字符段落一次地
        if (segment.length() > 0) {
            IrConstantString irConstantString =
                IrBuilder.GetNewIrConstantString(segment.toString());
            new PrintStrInstr(new GepInstr(irConstantString, new IrConstantInt(0)));
        }
    }
}
