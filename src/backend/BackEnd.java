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
import optimize.RegisterAllocator;
import optimize.IrSupport;
import optimize.FunctionEffects;

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
    private boolean optimized;
    private RegisterAllocator allocator;
    private IrBasicBlock nextBlock;
    private FunctionEffects effects;
    private final java.util.Set<CompareInstr> branchComparisons = new java.util.HashSet<>();
    private final java.util.Map<String, Long> dataOffsets = new java.util.HashMap<>();
    private long dataSize;
    private boolean usesGlobalBase;

    public static String GenerateMips(IrModule irModule) {
        return new BackEnd().lowerModule(irModule);
    }

    public static String GenerateMips(IrModule irModule, boolean optimized) {
        BackEnd backend = new BackEnd();
        backend.optimized = optimized;
        return backend.lowerModule(irModule);
    }

    private String lowerModule(IrModule irModule) {
        effects = optimized ? new FunctionEffects(irModule) : null;
        if (optimized) mips.data.append(".align 2\n__sysy_global_base:\n");
        for (IrGlobalValue global : irModule.GetGlobalValues()) {
            emitGlobal(global);
        }
        irModule.GetStringConstants().stream()
            .sorted(Comparator.comparing(IrValue::GetIrName))
            .forEach(this::emitString);

        mips.text.append(".globl main\n");
        mips.label("main");
        int globalBaseInitialization = mips.text.length();
        mips.transfer("jal", functionLabel(irModule.GetMainFunction()));
        mips.immediate("$v0", 10);
        mips.instruction("syscall");
        for (IrFunction function : irModule.GetFunctions()) {
            emitFunction(function);
        }
        runtime.emit(mips);
        if (usesGlobalBase) mips.text.insert(globalBaseInitialization, "    la $gp, __sysy_global_base\n");
        if (optimized && !System.getProperty("compiler.peephole", "on").equalsIgnoreCase("off")) {
            LocalPeephole.run(mips.text);
        }
        if (optimized) BranchSimplify.run(mips.text);
        // Course MARS 2026 runs with delayed branching disabled. Keep padding only
        // for an explicit compatibility check; remove it after branch rewriting.
        if (optimized && !Boolean.getBoolean("compiler.mips.delaySlots")) {
            String compact = mips.text.toString().lines()
                .filter(line -> !line.trim().equals("nop"))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
            mips.text.setLength(0);
            mips.text.append(compact);
        }
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
            dataSize = (dataSize + 3) & ~3L;
        }
        dataOffsets.put(label, dataSize);
        dataSize += ((long) values.size() + zeroCount) * width;
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
        branchComparisons.clear();
        if (optimized) {
            for (IrBasicBlock block : function.GetBasicBlocks()) {
                List<Instr> instructions = block.GetInstrList();
                if (instructions.size() < 2) continue;
                if (instructions.get(instructions.size() - 1) instanceof BranchInstr branch &&
                    branch.GetCond() instanceof CompareInstr compare &&
                    instructions.get(instructions.size() - 2) == compare &&
                    compare.GetUseList().size() == 1 &&
                    canFuseComparison(compare)) {
                    branchComparisons.add(compare);
                }
            }
        }
        allocator = optimized ? new RegisterAllocator(function) : null;
        frame = new MipsFrame(function, allocator);
        String label = functionLabel(function);
        returnLabel = "__sysy_return_" + label;
        int callsBefore = mips.callCount;
        mips.label(label);
        RecursiveMemoization memo = optimized ? RecursiveMemoization.create(function, effects) : null;
        if (memo != null) memo.emitLookup(mips);
        mips.addOffset("$sp", "$sp", -frame.size);
        int returnSaveStart = mips.text.length();
        mips.memory("sw", "$ra", "$sp", frame.returnAddress);
        int returnSaveEnd = mips.text.length();
        if (memo != null) memo.saveArguments(mips, frame);
        for (var saved : frame.savedOffsets.entrySet()) mips.memory("sw", saved.getKey(), "$sp", saved.getValue());
        if (optimized) {
            for (int i = 0; i < function.GetParameterList().size(); i++) {
                IrValue parameter = function.GetParameterList().get(i);
                String register = allocator.registers.get(parameter);
                if (i < 4) {
                    if (register != null) {
                        if (!register.equals("$a" + i)) mips.instruction("move", register, "$a" + i);
                    }
                    else mips.memory("sw", "$a" + i, "$sp", frame.offsetOf(parameter));
                } else if (register != null) mips.memory("lw", register, "$sp", frame.offsetOf(parameter));
            }
        }
        List<IrBasicBlock> blocks = optimized ? BlockLayout.order(function) : function.GetBasicBlocks();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            IrBasicBlock block = blocks.get(blockIndex);
            nextBlock = blockIndex + 1 < blocks.size() ? blocks.get(blockIndex + 1) : null;
            mips.label(block.GetIrName());
            for (Instr instr : block.GetInstrList()) {
                emitInstruction(instr);
            }
        }
        mips.label(returnLabel);
        if (memo != null) memo.emitStore(mips, frame);
        // Inspect emitted calls, including runtime helpers. Direct syscalls do
        // not overwrite $ra. Keep the frame layout and incoming argument offsets.
        boolean preserveReturn = !optimized || mips.callCount != callsBefore ||
            System.getProperty("compiler.leafReturn", "on").equalsIgnoreCase("off");
        if (!preserveReturn) mips.text.delete(returnSaveStart, returnSaveEnd);
        for (var saved : frame.savedOffsets.entrySet()) mips.memory("lw", saved.getKey(), "$sp", saved.getValue());
        if (preserveReturn) mips.memory("lw", "$ra", "$sp", frame.returnAddress);
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
                String result = resultRegister(instr);
                accessMemory(sizeOf(instr.GetIrType()) == 1 ? "lbu" : "lw", result, operands.get(0));
                saveResult(instr, result);
            }
            case STORE -> {
                IrValue pointer = operands.get(1);
                IrType type = ((IrPointerType) pointer.GetIrType()).getTargetType();
                String value = operandRegister(operands.get(0), "$t0", false);
                accessMemory(sizeOf(type) == 1 ? "sb" : "sw", value, pointer);
            }
            case ALU -> emitArithmetic((AluInstr) instr);
            case CMP -> { if (!branchComparisons.contains(instr)) emitCompare((CompareInstr) instr); }
            case EXTEND, TRUNC -> {
                String result = resultRegister(instr);
                loadValue(operands.get(0), result);
                saveResult(instr, result);
            }
            case GEP -> emitAddress((GepInstr) instr);
            case JUMP -> {
                IrBasicBlock target = ((JumpInstr) instr).GetTargetBlock();
                if (!optimized || target != nextBlock) mips.transfer("j", target.GetIrName());
            }
            case BRANCH -> emitBranch((BranchInstr) instr);
            case CALL -> emitCall((CallInstr) instr);
            case RETURN -> {
                IrValue value = ((ReturnInstr) instr).GetReturnValue();
                if (value != null) {
                    loadValue(value, "$v0");
                }
                if (!optimized || nextBlock != null) mips.transfer("j", returnLabel);
            }
            case IO -> emitBuiltin(instr, builtinForInstruction(instr), operands);
        }
    }

    private void emitArithmetic(AluInstr instr) {
        String op = instr.GetOpName();
        if (optimized && emitConstantArithmetic(instr)) return;
        boolean signed = op.equals("sdiv") || op.equals("srem");
        String left = operandRegister(instr.GetUseValueList().get(0), "$t0", signed);
        String right = operandRegister(instr.GetUseValueList().get(1), "$t1", signed);
        String result = resultRegister(instr);
        switch (op) {
            case "add" -> mips.instruction("addu", result, left, right);
            case "sub" -> mips.instruction("subu", result, left, right);
            case "mul" -> mips.instruction("mul", result, left, right);
            case "sdiv", "srem", "udiv", "urem" -> {
                mips.instruction(signed ? "div" : "divu", left, right);
                mips.instruction(op.endsWith("rem") ? "mfhi" : "mflo", result);
            }
            default -> throw new IllegalArgumentException("Unknown IR arithmetic operation: " + op);
        }
        saveResult(instr, result);
    }

    private boolean emitConstantArithmetic(AluInstr instr) {
        String op = instr.GetOpName();
        IrValue left = instr.GetUseValueList().get(0), right = instr.GetUseValueList().get(1);
        if (IrSupport.isNumber(left) && (op.equals("add") || op.equals("mul"))) {
            IrValue swap = left; left = right; right = swap;
        }
        if (!IrSupport.isNumber(right)) return false;
        int value = op.startsWith("s") ? IrSupport.signedNumber(right) : integerConstant(right);
        if ((op.equals("udiv") || op.equals("urem")) && value != 0 &&
            (value & (value - 1)) == 0 && instr.GetIrType().IsInt32Type()) {
            String source = operandRegister(left, "$t0", false), result = resultRegister(instr);
            if (op.equals("udiv")) mips.instruction("srl", result, source, Integer.toString(Integer.numberOfTrailingZeros(value)));
            else if (Integer.compareUnsigned(value - 1, 65535) <= 0) mips.instruction("andi", result, source, Integer.toString(value - 1));
            else {
                mips.immediate("$t1", value - 1);
                mips.instruction("and", result, source, "$t1");
            }
            saveResult(instr, result);
            return true;
        } else if ((op.equals("sdiv") || op.equals("srem")) && value != 0 && instr.GetIrType().IsInt32Type()) {
            loadValue(left, "$t0");
            ConstantArithmetic.divide(mips, value, op.equals("srem"));
        } else if (op.equals("mul")) {
            String source = operandRegister(left, "$t0", false);
            String result = resultRegister(instr);
            long magnitude = Math.abs((long) value);
            if (value == 0) mips.instruction("move", result, "$zero");
            else if ((magnitude & (magnitude - 1)) == 0) {
                int shift = Long.numberOfTrailingZeros(magnitude);
                if (shift == 0) {
                    if (!result.equals(source)) mips.instruction("move", result, source);
                } else mips.instruction("sll", result, source, Integer.toString(shift));
                if (value < 0) mips.instruction("subu", result, "$zero", result);
            } else if (!ConstantArithmetic.multiplySmall(mips, source, result, value)) {
                mips.immediate("$t1", value);
                mips.instruction("mul", result, source, "$t1");
            }
            saveResult(instr, result);
            return true;
        } else if ((op.equals("add") || op.equals("sub")) &&
            (op.equals("sub") ? -(long) value : value) >= Short.MIN_VALUE &&
            (op.equals("sub") ? -(long) value : value) <= Short.MAX_VALUE) {
            String source = operandRegister(left, "$t0", false);
            String result = resultRegister(instr);
            mips.addOffset(result, source, op.equals("sub") ? -value : value);
            saveResult(instr, result);
            return true;
        } else return false;
        saveResult(instr, "$t0");
        return true;
    }

    private void emitCompare(CompareInstr instr) {
        if (optimized && emitImmediateCompare(instr)) return;
        CompareInstr.CompareOp op = instr.GetCompareOp();
        boolean signed = switch (op) {
            case SGT, SGE, SLT, SLE -> true;
            default -> false;
        };
        String left = operandRegister(instr.GetUseValueList().get(0), "$t0", signed);
        String right = operandRegister(instr.GetUseValueList().get(1), "$t1", signed);
        String result = resultRegister(instr);
        String lessThan = signed ? "slt" : "sltu";
        switch (op) {
            case EQ -> mips.instruction("seq", result, left, right);
            case NE -> mips.instruction("sne", result, left, right);
            case SLT, SGE, ULT, UGE ->
                mips.instruction(lessThan, result, left, right);
            case SGT, SLE, UGT, ULE ->
                mips.instruction(lessThan, result, right, left);
        }
        switch (op) {
            case SGE, SLE, UGE, ULE -> mips.instruction("xori", result, result, "1");
            default -> { }
        }
        saveResult(instr, result);
    }

    private boolean emitImmediateCompare(CompareInstr instr) {
        IrValue left = instr.GetUseValueList().get(0), right = instr.GetUseValueList().get(1);
        CompareInstr.CompareOp op = instr.GetCompareOp();
        if (IrSupport.isNumber(left) && !IrSupport.isNumber(right)) {
            IrValue swap = left; left = right; right = swap;
            op = switch (op) {
                case SLT -> CompareInstr.CompareOp.SGT; case SGT -> CompareInstr.CompareOp.SLT;
                case SLE -> CompareInstr.CompareOp.SGE; case SGE -> CompareInstr.CompareOp.SLE;
                default -> op;
            };
        }
        if (!IrSupport.isNumber(right)) return false;
        boolean invert;
        long threshold = IrSupport.signedNumber(right);
        switch (op) {
            case SLT -> invert = false;
            case SGE -> invert = true;
            case SLE -> { threshold++; invert = false; }
            case SGT -> { threshold++; invert = true; }
            default -> { return false; }
        }
        if (threshold < Short.MIN_VALUE || threshold > Short.MAX_VALUE) return false;
        String source = operandRegister(left, "$t0", true), result = resultRegister(instr);
        mips.instruction("slti", result, source, Long.toString(threshold));
        if (invert) mips.instruction("xori", result, result, "1");
        saveResult(instr, result);
        return true;
    }

    private boolean canFuseComparison(CompareInstr compare) {
        if (!System.getProperty("compiler.branchFusion", "on").equalsIgnoreCase("off")) return true;
        switch (compare.GetCompareOp()) {
            case EQ, NE -> { return true; }
            case SLT, SGT, SLE, SGE -> {
                return compare.GetUseValueList().stream().anyMatch(v -> IrSupport.isNumber(v) && integerConstant(v) == 0);
            }
            default -> { return false; }
        }
    }

    private String operandRegister(IrValue value, String scratch, boolean signed) {
        boolean needsExtension = signed && (value.GetIrType().IsInt8Type() || value.GetIrType().IsInt1Type());
        Integer offset = globalOffset(frame.constantAddress(value));
        if (offset != null && offset == 0) return "$gp";
        if (optimized && !needsExtension && frame.constantAddress(value) == null) {
            String register = allocator.registers.get(value);
            if (register != null) return register;
            if (IrSupport.isNumber(value) && integerConstant(value) == 0) return "$zero";
        }
        loadValue(value, scratch);
        if (needsExtension) signExtend(value.GetIrType(), scratch);
        return scratch;
    }

    private String resultRegister(Instr instr) {
        return optimized ? allocator.registers.getOrDefault(instr, "$t0") : "$t0";
    }

    private void emitAddress(GepInstr instr) {
        if (frame.constantAddress(instr) != null) {
            return;
        }
        IrValue base = instr.GetUseValueList().get(0);
        IrValue index = instr.GetUseValueList().get(1);
        String pointer = operandRegister(base, "$t0", false);
        String offset = operandRegister(index, "$t1", true);
        String result = resultRegister(instr);
        int width = sizeOf(GepInstr.GetTargetType(base));
        if (width == WORD_BYTES) {
            mips.instruction("sll", "$t1", offset, "2");
            offset = "$t1";
        } else if (width != 1) {
            mips.immediate("$t2", width);
            mips.instruction("mul", "$t1", offset, "$t2");
            offset = "$t1";
        }
        mips.instruction("addu", result, pointer, offset);
        saveResult(instr, result);
    }

    private void emitBranch(BranchInstr instr) {
        if (instr.GetCond() instanceof CompareInstr compare && branchComparisons.contains(compare)) {
            if (compare.GetCompareOp() == CompareInstr.CompareOp.EQ || compare.GetCompareOp() == CompareInstr.CompareOp.NE) {
                String left = operandRegister(compare.GetUseValueList().get(0), "$t0", false);
                String right = operandRegister(compare.GetUseValueList().get(1), "$t1", false);
                emitFusedBranch(instr, compare.GetCompareOp() == CompareInstr.CompareOp.EQ ? "beq" : "bne", left, right);
            } else if (switch (compare.GetCompareOp()) {
                case SLT, SGT, SLE, SGE -> compare.GetUseValueList().stream()
                    .anyMatch(v -> IrSupport.isNumber(v) && integerConstant(v) == 0);
                default -> false;
            }) {
                IrValue left = compare.GetUseValueList().get(0), right = compare.GetUseValueList().get(1);
                boolean reversed = IrSupport.isNumber(left) && integerConstant(left) == 0;
                String source = operandRegister(reversed ? right : left, "$t0", true);
                String op = switch (compare.GetCompareOp()) {
                    case SLT -> reversed ? "bgtz" : "bltz";
                    case SGT -> reversed ? "bltz" : "bgtz";
                    case SLE -> reversed ? "bgez" : "blez";
                    case SGE -> reversed ? "blez" : "bgez";
                    default -> throw new IllegalStateException();
                };
                emitFusedBranch(instr, op, source, null);
            } else {
                emitRelationalBranch(instr, compare);
            }
            return;
        }
        String condition = operandRegister(instr.GetCond(), "$t0", false);
        if (optimized) {
            // Keep the conditional branch local to preserve large-function support.
            if (instr.GetTrueBlock() == nextBlock) {
                String fallthrough = "__sysy_branch_" + nextBranchId++;
                mips.transfer("bne", condition, "$zero", fallthrough);
                mips.transfer("j", instr.GetFalseBlock().GetIrName());
                mips.label(fallthrough);
                return;
            }
            if (instr.GetFalseBlock() == nextBlock) {
                String fallthrough = "__sysy_branch_" + nextBranchId++;
                mips.transfer("beq", condition, "$zero", fallthrough);
                mips.transfer("j", instr.GetTrueBlock().GetIrName());
                mips.label(fallthrough);
                return;
            }
        }
        // The conditional jump only spans a few instructions, including in large functions.
        String falsePath = "__sysy_branch_" + nextBranchId++;
        mips.transfer("beq", condition, "$zero", falsePath);
        mips.transfer("j", instr.GetTrueBlock().GetIrName());
        mips.label(falsePath);
        mips.transfer("j", instr.GetFalseBlock().GetIrName());
    }

    /** A branch-only predicate needs neither a stored boolean nor an explicit NOT. */
    private void emitRelationalBranch(BranchInstr branch, CompareInstr compare) {
        IrValue left = compare.GetUseValueList().get(0), right = compare.GetUseValueList().get(1);
        CompareInstr.CompareOp op = compare.GetCompareOp();
        boolean signed = switch (op) {
            case SLT, SLE, SGT, SGE -> true;
            default -> false;
        };
        if (signed && IrSupport.isNumber(left) && !IrSupport.isNumber(right)) {
            IrValue swap = left; left = right; right = swap;
            op = switch (op) {
                case SLT -> CompareInstr.CompareOp.SGT; case SGT -> CompareInstr.CompareOp.SLT;
                case SLE -> CompareInstr.CompareOp.SGE; case SGE -> CompareInstr.CompareOp.SLE;
                default -> throw new IllegalStateException();
            };
        }
        // Preserve the existing immediate selection, computing k+1 in 64 bits
        // so an INT_MAX boundary cannot silently turn into INT_MIN.
        if (signed && IrSupport.isNumber(right)) {
            long threshold = IrSupport.signedNumber(right);
            if (op == CompareInstr.CompareOp.SLE || op == CompareInstr.CompareOp.SGT) threshold++;
            if (threshold >= Short.MIN_VALUE && threshold <= Short.MAX_VALUE) {
                String source = operandRegister(left, "$t0", true);
                mips.instruction("slti", "$t2", source, Long.toString(threshold));
                boolean invert = op == CompareInstr.CompareOp.SGE || op == CompareInstr.CompareOp.SGT;
                emitFusedBranch(branch, invert ? "beq" : "bne", "$t2", "$zero");
                return;
            }
        }
        String lhs = operandRegister(left, "$t0", signed);
        String rhs = operandRegister(right, "$t1", signed);
        boolean reverse = switch (op) {
            case SGT, SLE, UGT, ULE -> true;
            default -> false;
        };
        boolean invert = switch (op) {
            case SLE, SGE, ULE, UGE -> true;
            default -> false;
        };
        mips.instruction(signed ? "slt" : "sltu", "$t2", reverse ? rhs : lhs, reverse ? lhs : rhs);
        emitFusedBranch(branch, invert ? "beq" : "bne", "$t2", "$zero");
    }

    private void emitFusedBranch(BranchInstr branch, String operation, String left, String right) {
        String label = "__sysy_branch_" + nextBranchId++;
        boolean trueFallsThrough = branch.GetTrueBlock() == nextBlock;
        String op = trueFallsThrough ? operation : BranchSimplify.inverse(operation);
        if (right == null) mips.transfer(op, left, label);
        else mips.transfer(op, left, right, label);
        mips.transfer("j", (trueFallsThrough ? branch.GetFalseBlock() : branch.GetTrueBlock()).GetIrName());
        mips.label(label);
        if (!trueFallsThrough && branch.GetFalseBlock() != nextBlock) mips.transfer("j", branch.GetFalseBlock().GetIrName());
    }

    private void emitCall(CallInstr instr) {
        List<IrValue> args = instr.GetParamList();
        Builtin builtin = Builtin.forFunction(instr.GetTargetFunction().GetIrName());
        if (builtin != null) {
            emitBuiltin(instr, builtin, args);
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            if (optimized && i < 4) loadValue(args.get(i), "$a" + i);
            else {
                loadValue(args.get(i), "$t0");
                mips.memory("sw", "$t0", "$sp", i * WORD_BYTES);
            }
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
        if (optimized && builtin == Builtin.PUT_STRING && asciiString(args.get(0))) {
            mips.immediate("$v0", 4);
            mips.instruction("syscall");
        } else runtime.call(mips, builtin);
        if (instr.DefValue()) {
            saveResult(instr, "$v0");
        }
    }

    private boolean asciiString(IrValue pointer) {
        while (pointer instanceof GepInstr gep) {
            if (!IrSupport.isNumber(gep.GetUseValueList().get(1)) || integerConstant(gep.GetUseValueList().get(1)) != 0) return false;
            pointer = gep.GetUseValueList().get(0);
        }
        if (!(pointer instanceof IrConstantString string)) return false;
        return string.GetStringValue().chars().allMatch(c -> c < 128);
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
            Integer offset = globalOffset(address);
            if (offset != null) {
                mips.addOffset(register, "$gp", offset);
            } else if (address.label() == null) {
                mips.addOffset(register, "$sp", address.offset());
            } else {
                mips.instruction("la", register, address.label());
                if (address.offset() != 0) {
                    mips.addOffset(register, register, address.offset());
                }
            }
        } else if (value instanceof IrConstantInt || value instanceof IrConstantChar) {
            mips.immediate(register, integerConstant(value));
        } else if (optimized && allocator.registers.containsKey(value)) {
            String source = allocator.registers.get(value);
            if (!source.equals(register)) mips.instruction("move", register, source);
        } else {
            mips.memory("lw", register, "$sp", frame.offsetOf(value));
        }
    }

    private void accessMemory(String op, String register, IrValue pointer) {
        if (optimized && allocator.slots.contains(pointer) && allocator.registers.containsKey(pointer)) {
            String allocated = allocator.registers.get(pointer);
            if (op.equals("sw") || op.equals("sb")) {
                if (op.equals("sb")) mips.instruction("andi", allocated, register, "255");
                else if (!allocated.equals(register)) mips.instruction("move", allocated, register);
            } else if (!allocated.equals(register)) mips.instruction("move", register, allocated);
            return;
        }
        MipsFrame.Address address = frame.constantAddress(pointer);
        Integer offset = globalOffset(address);
        if (offset != null) {
            mips.memory(op, register, "$gp", offset);
        } else if (address != null && address.label() == null) {
            mips.memory(op, register, "$sp", address.offset());
        } else {
            String base = operandRegister(pointer, "$t1", false);
            mips.memory(op, register, base, 0);
        }
    }

    private Integer globalOffset(MipsFrame.Address address) {
        if (!optimized || address == null || address.label() == null) return null;
        Long base = dataOffsets.get(address.label());
        if (base == null) return null;
        long offset = base + address.offset();
        if (offset < Short.MIN_VALUE || offset > Short.MAX_VALUE) return null;
        usesGlobalBase = true;
        return (int) offset;
    }

    private void saveResult(Instr instr, String register) {
        IrType type = instr.GetIrType();
        if ((type.IsInt8Type() || type.IsInt1Type()) && !(optimized && instr instanceof CompareInstr)) {
            mips.instruction("andi", register, register, type.IsInt8Type() ? "255" : "1");
        }
        if (optimized && allocator.registers.containsKey(instr)) {
            String target = allocator.registers.get(instr);
            if (!target.equals(register)) mips.instruction("move", target, register);
        } else mips.memory("sw", register, "$sp", frame.offsetOf(instr));
    }

    private void signExtend(IrType type, String register) {
        if (type.IsInt8Type() || type.IsInt1Type()) {
            String shift = type.IsInt8Type() ? "24" : "31";
            mips.instruction("sll", register, register, shift);
            mips.instruction("sra", register, register, shift);
        }
    }
}
