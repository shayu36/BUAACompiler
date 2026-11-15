package backend;

import midend.llvm.IrModule;
import midend.llvm.constant.IrConstant;
import midend.llvm.constant.IrConstantArray;
import midend.llvm.constant.IrConstantChar;
import midend.llvm.constant.IrConstantInt;
import midend.llvm.constant.IrConstantString;
import midend.llvm.instr.AluInstr;
import midend.llvm.instr.BranchInstr;
import midend.llvm.instr.CallInstr;
import midend.llvm.instr.CompareInstr;
import midend.llvm.instr.GepInstr;
import midend.llvm.instr.Instr;
import midend.llvm.instr.JumpInstr;
import midend.llvm.instr.ReturnInstr;
import midend.llvm.instr.io.GetCharInstr;
import midend.llvm.instr.io.GetIntInstr;
import midend.llvm.instr.io.GetStringInstr;
import midend.llvm.instr.io.PrintCharInstr;
import midend.llvm.instr.io.PrintIntInstr;
import midend.llvm.instr.io.PrintStrInstr;
import midend.llvm.type.IrArrayType;
import midend.llvm.type.IrPointerType;
import midend.llvm.type.IrType;
import midend.llvm.value.IrBasicBlock;
import midend.llvm.value.IrFunction;
import midend.llvm.value.IrGlobalValue;
import midend.llvm.value.IrValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static backend.MipsFrame.WORD_BYTES;
import static backend.MipsFrame.integerConstant;
import static backend.MipsFrame.sizeOf;
import static backend.MipsRuntime.Builtin;

public final class BackEnd {
    private final MipsModule mips = new MipsModule();
    private final MipsRuntime runtime = new MipsRuntime();
    private MipsFrame frame;
    private String returnLabel;
    private int nextBranchId;

    public static String GenerateMips(IrModule irModule) {
        return new BackEnd().lowerModule(irModule);
    }

    private String lowerModule(IrModule irModule) {
        for (IrGlobalValue global : irModule.GetGlobalValues()) {
            emitGlobal(global);
        }
        irModule.GetStringConstants().stream()
            .sorted(Comparator.comparing(IrValue::GetIrName))
            .forEach(this::emitString);

        mips.text.append(".globl main\n");
        mips.label("main");
        mips.transfer("jal", functionLabel(irModule.GetMainFunction()));
        mips.immediate("$v0", 10);
        mips.instruction("syscall");
        for (IrFunction function : irModule.GetFunctions()) {
            emitFunction(function);
        }
        runtime.emit(mips);
        return mips.toString();
    }

    private void emitGlobal(IrGlobalValue global) {
        IrConstant initializer = global.GetInitializer();
        if (initializer instanceof IrConstantArray array) {
            IrType elementType = ((IrArrayType) array.GetIrType()).GetElementType();
            List<IrConstant> values = array.GetValues();
            int count = Math.min(values.size(), array.GetArraySize());
            while (count > 0 && integerConstant(values.get(count - 1)) == 0) {
                count--;
            }
            List<Integer> data = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                data.add(integerConstant(values.get(i)));
            }
            emitData(global.GetMipsLabel(), sizeOf(elementType), data, array.GetArraySize() - count);
        } else {
            emitData(global.GetMipsLabel(), sizeOf(initializer.GetIrType()),
                List.of(integerConstant(initializer)), 0);
        }
    }

    private void emitString(IrConstantString constant) {
        String raw = constant.GetStringValue();
        List<Integer> bytes = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == 'n') {
                bytes.add((int) '\n');
                i++;
            } else {
                bytes.add(ch & 0xff);
            }
        }
        bytes.add(0);
        emitData(constant.GetMipsLabel(), 1, bytes, 0);
    }

    private void emitData(String label, int width, List<Integer> values, int zeroCount) {
        if (width == WORD_BYTES) {
            mips.data.append(".align 2\n");
        }
        mips.data.append(label).append(":\n");
        String directive = width == 1 ? "    .byte " : "    .word ";
        for (int i = 0; i < values.size(); i++) {
            int value = values.get(i);
            // MARS accepts signed byte literals without assembler warnings.
            mips.data.append(i % 16 == 0 ? directive : ", ").append(width == 1 ? (byte) value : value);
            if (i % 16 == 15 || i + 1 == values.size()) {
                mips.data.append('\n');
            }
        }
        if (zeroCount > 0 || values.isEmpty()) {
            mips.data.append("    .space ").append(zeroCount * width).append('\n');
        }
    }

    private void emitFunction(IrFunction function) {
        frame = new MipsFrame(function);
        String label = functionLabel(function);
        returnLabel = "__sysy_return_" + label;
        mips.label(label);
        mips.addOffset("$sp", "$sp", -frame.size);
        mips.memory("sw", "$ra", "$sp", frame.returnAddress);
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            mips.label(block.GetIrName());
            for (Instr instr : block.GetInstrList()) {
                emitInstruction(instr);
            }
        }
        mips.label(returnLabel);
        mips.memory("lw", "$ra", "$sp", frame.returnAddress);
        mips.addOffset("$sp", "$sp", frame.size);
        mips.transfer("jr", "$ra");
    }

    private static String functionLabel(IrFunction function) {
        return function.IsMainFunction() ? "__sysy_main" : function.GetMipsLabel();
    }

    private void emitInstruction(Instr instr) {
        List<IrValue> operands = instr.GetUseValueList();
        switch (instr.GetInstrType()) {
            case ALLOCATE -> { } // Space is reserved in the function frame.
            case LOAD -> {
                accessMemory(sizeOf(instr.GetIrType()) == 1 ? "lbu" : "lw", "$t0", operands.get(0));
                saveResult(instr, "$t0");
            }
            case STORE -> {
                IrValue pointer = operands.get(1);
                IrType type = ((IrPointerType) pointer.GetIrType()).getTargetType();
                loadValue(operands.get(0), "$t0");
                accessMemory(sizeOf(type) == 1 ? "sb" : "sw", "$t0", pointer);
            }
            case ALU -> emitArithmetic((AluInstr) instr);
            case CMP -> emitCompare((CompareInstr) instr);
            case EXTEND, TRUNC -> {
                loadValue(operands.get(0), "$t0");
                saveResult(instr, "$t0");
            }
            case GEP -> emitAddress((GepInstr) instr);
            case JUMP -> mips.transfer("j", ((JumpInstr) instr).GetTargetBlock().GetIrName());
            case BRANCH -> emitBranch((BranchInstr) instr);
            case CALL -> emitCall((CallInstr) instr);
            case RETURN -> {
                IrValue value = ((ReturnInstr) instr).GetReturnValue();
                if (value != null) {
                    loadValue(value, "$v0");
                }
                mips.transfer("j", returnLabel);
            }
            case IO -> emitBuiltin(instr, builtinForInstruction(instr), operands);
        }
    }

    private void emitArithmetic(AluInstr instr) {
        String op = instr.GetOpName();
        boolean signed = op.equals("sdiv") || op.equals("srem");
        loadBinaryOperands(instr, signed);
        switch (op) {
            case "add" -> mips.instruction("addu", "$t0", "$t0", "$t1");
            case "sub" -> mips.instruction("subu", "$t0", "$t0", "$t1");
            case "mul" -> mips.instruction("mul", "$t0", "$t0", "$t1");
            case "sdiv", "srem", "udiv", "urem" -> {
                mips.instruction(signed ? "div" : "divu", "$t0", "$t1");
                mips.instruction(op.endsWith("rem") ? "mfhi" : "mflo", "$t0");
            }
            default -> throw new IllegalArgumentException("Unknown IR arithmetic operation: " + op);
        }
        saveResult(instr, "$t0");
    }

    private void emitCompare(CompareInstr instr) {
        CompareInstr.CompareOp op = instr.GetCompareOp();
        boolean signed = switch (op) {
            case SGT, SGE, SLT, SLE -> true;
            default -> false;
        };
        loadBinaryOperands(instr, signed);
        String lessThan = signed ? "slt" : "sltu";
        switch (op) {
            case EQ -> mips.instruction("seq", "$t0", "$t0", "$t1");
            case NE -> mips.instruction("sne", "$t0", "$t0", "$t1");
            case SLT, SGE, ULT, UGE ->
                mips.instruction(lessThan, "$t0", "$t0", "$t1");
            case SGT, SLE, UGT, ULE ->
                mips.instruction(lessThan, "$t0", "$t1", "$t0");
        }
        switch (op) {
            case SGE, SLE, UGE, ULE -> mips.instruction("xori", "$t0", "$t0", "1");
            default -> { }
        }
        saveResult(instr, "$t0");
    }

    private void loadBinaryOperands(Instr instr, boolean signed) {
        IrValue left = instr.GetUseValueList().get(0);
        IrValue right = instr.GetUseValueList().get(1);
        loadValue(left, "$t0");
        loadValue(right, "$t1");
        if (signed) {
            signExtend(left.GetIrType(), "$t0");
            signExtend(right.GetIrType(), "$t1");
        }
    }

    private void emitAddress(GepInstr instr) {
        if (frame.constantAddress(instr) != null) {
            return;
        }
        IrValue base = instr.GetUseValueList().get(0);
        IrValue index = instr.GetUseValueList().get(1);
        loadValue(base, "$t0");
        loadValue(index, "$t1");
        signExtend(index.GetIrType(), "$t1");
        int width = sizeOf(GepInstr.GetTargetType(base));
        if (width == WORD_BYTES) {
            mips.instruction("sll", "$t1", "$t1", "2");
        } else if (width != 1) {
            mips.immediate("$t2", width);
            mips.instruction("mul", "$t1", "$t1", "$t2");
        }
        mips.instruction("addu", "$t0", "$t0", "$t1");
        saveResult(instr, "$t0");
    }

    private void emitBranch(BranchInstr instr) {
        loadValue(instr.GetCond(), "$t0");
        // The conditional jump only spans a few instructions, including in large functions.
        String falsePath = "__sysy_branch_" + nextBranchId++;
        mips.transfer("beq", "$t0", "$zero", falsePath);
        mips.transfer("j", instr.GetTrueBlock().GetIrName());
        mips.label(falsePath);
        mips.transfer("j", instr.GetFalseBlock().GetIrName());
    }

    private void emitCall(CallInstr instr) {
        List<IrValue> args = instr.GetParamList();
        Builtin builtin = Builtin.forFunction(instr.GetTargetFunction().GetIrName());
        if (builtin != null) {
            emitBuiltin(instr, builtin, args);
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            loadValue(args.get(i), "$t0");
            mips.memory("sw", "$t0", "$sp", i * WORD_BYTES);
        }
        mips.transfer("jal", functionLabel(instr.GetTargetFunction()));
        if (instr.DefValue()) {
            saveResult(instr, "$v0");
        }
    }

    private void emitBuiltin(Instr instr, Builtin builtin, List<IrValue> args) {
        for (int i = 0; i < args.size(); i++) {
            loadValue(args.get(i), "$a" + i);
        }
        runtime.call(mips, builtin);
        if (instr.DefValue()) {
            saveResult(instr, "$v0");
        }
    }

    private static Builtin builtinForInstruction(Instr instr) {
        if (instr instanceof GetCharInstr) {
            return Builtin.GET_CHAR;
        } else if (instr instanceof GetIntInstr) {
            return Builtin.GET_INT;
        } else if (instr instanceof GetStringInstr) {
            return Builtin.GET_STRING;
        } else if (instr instanceof PrintCharInstr) {
            return Builtin.PUT_CHAR;
        } else if (instr instanceof PrintIntInstr) {
            return Builtin.PUT_INT;
        } else if (instr instanceof PrintStrInstr) {
            return Builtin.PUT_STRING;
        }
        throw new IllegalArgumentException("Unknown IR I/O instruction: " + instr.getClass().getName());
    }

    private void loadValue(IrValue value, String register) {
        MipsFrame.Address address = frame.constantAddress(value);
        if (address != null) {
            if (address.label() == null) {
                mips.addOffset(register, "$sp", address.offset());
            } else {
                mips.instruction("la", register, address.label());
                if (address.offset() != 0) {
                    mips.addOffset(register, register, address.offset());
                }
            }
        } else if (value instanceof IrConstantInt || value instanceof IrConstantChar) {
            mips.immediate(register, integerConstant(value));
        } else {
            mips.memory("lw", register, "$sp", frame.offsetOf(value));
        }
    }

    private void accessMemory(String op, String register, IrValue pointer) {
        MipsFrame.Address address = frame.constantAddress(pointer);
        if (address != null && address.label() == null) {
            mips.memory(op, register, "$sp", address.offset());
        } else {
            loadValue(pointer, "$t1");
            mips.memory(op, register, "$t1", 0);
        }
    }

    private void saveResult(Instr instr, String register) {
        IrType type = instr.GetIrType();
        if (type.IsInt8Type() || type.IsInt1Type()) {
            mips.instruction("andi", register, register, type.IsInt8Type() ? "255" : "1");
        }
        mips.memory("sw", register, "$sp", frame.offsetOf(instr));
    }

    private void signExtend(IrType type, String register) {
        if (type.IsInt8Type() || type.IsInt1Type()) {
            String shift = type.IsInt8Type() ? "24" : "31";
            mips.instruction("sll", register, register, shift);
            mips.instruction("sra", register, register, shift);
        }
    }
}
