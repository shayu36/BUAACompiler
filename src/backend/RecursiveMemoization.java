package backend;

import midend.llvm.instr.CallInstr;
import midend.llvm.instr.Instr;
import midend.llvm.value.IrBasicBlock;
import midend.llvm.value.IrFunction;
import midend.llvm.value.IrParameter;
import optimize.FunctionEffects;

/** Bounded, exact-key cache for branching recursion with no observable side effects. */
final class RecursiveMemoization {
    private static final int ENTRIES = 256;
    private final IrFunction function;
    private final String table;

    private RecursiveMemoization(IrFunction function) {
        this.function = function;
        this.table = "__sysy_memo_" + function.GetMipsLabel();
    }

    static RecursiveMemoization create(IrFunction function, FunctionEffects effects) {
        int parameters = function.GetParameterList().size();
        if (!effects.isPure(function) || !function.GetReturnType().IsInt32Type() ||
            parameters < 1 || parameters > 2) return null;
        for (IrParameter parameter : function.GetParameterList()) {
            if (!parameter.GetIrType().IsInt32Type()) return null;
        }
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            int recursiveCalls = 0;
            for (Instr instr : block.GetInstrList()) {
                if (instr instanceof CallInstr call && call.GetTargetFunction() == function) recursiveCalls++;
            }
            if (recursiveCalls >= 2) return new RecursiveMemoization(function);
        }
        return null;
    }

    void emitLookup(MipsModule mips) {
        mips.data.append(".align 2\n").append(table).append(": .space ").append(ENTRIES * 16).append('\n');
        address(mips);
        String miss = table + "_miss";
        mips.memory("lw", "$t2", "$t1", 0);
        mips.transfer("beq", "$t2", "$zero", miss);
        for (int i = 0; i < function.GetParameterList().size(); i++) {
            mips.memory("lw", "$t2", "$t1", 4 + 4 * i);
            mips.transfer("bne", "$t2", "$a" + i, miss);
        }
        mips.memory("lw", "$v0", "$t1", 12);
        mips.transfer("jr", "$ra");
        mips.label(miss);
    }

    void saveArguments(MipsModule mips, MipsFrame frame) {
        for (int i = 0; i < function.GetParameterList().size(); i++) {
            mips.memory("sw", "$a" + i, "$sp", frame.offsetOf(function.GetParameterList().get(i)));
        }
    }

    void emitStore(MipsModule mips, MipsFrame frame) {
        for (int i = 0; i < function.GetParameterList().size(); i++) {
            mips.memory("lw", "$a" + i, "$sp", frame.offsetOf(function.GetParameterList().get(i)));
        }
        address(mips);
        for (int i = 0; i < function.GetParameterList().size(); i++) {
            mips.memory("sw", "$a" + i, "$t1", 4 + 4 * i);
        }
        mips.memory("sw", "$v0", "$t1", 12);
        mips.immediate("$t2", 1);
        mips.memory("sw", "$t2", "$t1", 0);
    }

    private void address(MipsModule mips) {
        mips.instruction("move", "$t0", "$a0");
        if (function.GetParameterList().size() == 2) {
            mips.instruction("sll", "$t2", "$a1", "5");
            mips.instruction("subu", "$t2", "$t2", "$a1");
            mips.instruction("xor", "$t0", "$t0", "$t2");
        }
        mips.instruction("andi", "$t0", "$t0", Integer.toString(ENTRIES - 1));
        mips.instruction("sll", "$t0", "$t0", "4");
        mips.instruction("la", "$t1", table);
        mips.instruction("addu", "$t1", "$t1", "$t0");
    }
}
