package midend.llvm.value;

import midend.llvm.instr.BranchInstr;
import midend.llvm.instr.Instr;
import midend.llvm.instr.JumpInstr;
import midend.llvm.instr.ReturnInstr;
import midend.llvm.type.IrBasicBlockType;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class IrBasicBlock extends IrValue {
    private final ArrayList<Instr> instrList;
    private final IrFunction irFunction;

    public IrBasicBlock(String irName, IrFunction irFunction) {
        super(IrBasicBlockType.BASIC_BLOCK, irName);
        this.instrList = new ArrayList<>();
        this.irFunction = irFunction;
    }

    public void AddInstr(Instr instr) {
        instrList.add(instr);
        instr.SetInBasicBlock(this);
    }

    // 插到 terminator 之前：入口块末尾可能已有 br，hoist 进来的 alloca 必须排它前面
    public void AddInstrBeforeTerminator(Instr instr) {
        int size = instrList.size();
        if (size > 0 && IsTerminator(instrList.get(size - 1))) {
            instrList.add(size - 1, instr);
        } else {
            instrList.add(instr);
        }
        instr.SetInBasicBlock(this);
    }

    private boolean IsTerminator(Instr instr) {
        return instr instanceof ReturnInstr ||
            instr instanceof BranchInstr ||
            instr instanceof JumpInstr;
    }

    public boolean IsEmptyBlock() {
        return instrList.isEmpty();
    }

    public boolean LastInstrIsReturn() {
        if (instrList.isEmpty()) {
            return false;
        }
        return instrList.get(instrList.size() - 1) instanceof ReturnInstr;
    }

    // 最后一条指令是不是 terminator（ret/br/jump 任一）
    public boolean LastInstrIsTerminator() {
        if (instrList.isEmpty()) {
            return false;
        }
        return IsTerminator(instrList.get(instrList.size() - 1));
    }

    public ArrayList<Instr> GetInstrList() {
        return instrList;
    }

    public IrFunction GetIrFunction() {
        return irFunction;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.irName);
        builder.append(":\n\t");
        builder.append(this.instrList.stream().map(Instr::toString).
            collect(Collectors.joining("\n\t")));
        return builder.toString();
    }
}
