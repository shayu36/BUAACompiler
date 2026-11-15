package backend;

import midend.llvm.constant.IrConstantChar;
import midend.llvm.constant.IrConstantInt;
import midend.llvm.constant.IrConstantString;
import midend.llvm.instr.AllocateInstr;
import midend.llvm.instr.CallInstr;
import midend.llvm.instr.GepInstr;
import midend.llvm.instr.Instr;
import midend.llvm.type.IrArrayType;
import midend.llvm.type.IrType;
import midend.llvm.value.IrBasicBlock;
import midend.llvm.value.IrFunction;
import midend.llvm.value.IrGlobalValue;
import midend.llvm.value.IrValue;

import java.util.HashMap;
import java.util.Map;

final class MipsFrame {
    static final int WORD_BYTES = 4;

    record Address(String label, int offset) {
        // A null label denotes an address relative to $sp.
    }

    private final Map<IrValue, Integer> offsets = new HashMap<>();
    final int size;
    final int returnAddress;

    MipsFrame(IrFunction function) {
        // The caller's outgoing argument area starts at the callee's incoming $sp.
        int outgoingWords = 0;
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr instanceof CallInstr call &&
                    MipsRuntime.Builtin.forFunction(call.GetTargetFunction().GetIrName()) == null) {
                    outgoingWords = Math.max(outgoingWords, call.GetParamList().size());
                }
            }
        }
        int offset = outgoingWords * WORD_BYTES;
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr instanceof AllocateInstr allocation) {
                    offsets.put(allocation, offset);
                    offset += align(Math.max(1, sizeOf(allocation.GetTargetType())), WORD_BYTES);
                }
            }
        }
        // Constant addresses can be rematerialized. Other SSA results have word slots.
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr.DefValue() && constantAddress(instr) == null) {
                    offsets.put(instr, offset);
                    offset += WORD_BYTES;
                }
            }
        }
        returnAddress = offset;
        size = align(offset + WORD_BYTES, 8);
        for (int i = 0; i < function.GetParameterList().size(); i++) {
            offsets.put(function.GetParameterList().get(i), size + i * WORD_BYTES);
        }
    }

    int offsetOf(IrValue value) {
        Integer offset = offsets.get(value);
        if (offset == null) {
            throw new IllegalStateException("Missing stack slot for " + value.GetIrName());
        }
        return offset;
    }

    Address constantAddress(IrValue value) {
        if (value instanceof AllocateInstr) {
            return new Address(null, offsetOf(value));
        }
        if (value instanceof IrGlobalValue || value instanceof IrConstantString) {
            return new Address(value.GetMipsLabel(), 0);
        }
        if (value instanceof GepInstr gep) {
            IrValue pointer = gep.GetUseValueList().get(0);
            IrValue index = gep.GetUseValueList().get(1);
            if (index instanceof IrConstantInt || index instanceof IrConstantChar) {
                Address base = constantAddress(pointer);
                if (base != null) {
                    int signedIndex = integerConstant(index);
                    if (index.GetIrType().IsInt8Type()) {
                        signedIndex = (byte) signedIndex;
                    }
                    int displacement = signedIndex * sizeOf(GepInstr.GetTargetType(pointer));
                    return new Address(base.label(), base.offset() + displacement);
                }
            }
        }
        return null;
    }

    static int sizeOf(IrType type) {
        if (type instanceof IrArrayType array) {
            return array.GetArraySize() * sizeOf(array.GetElementType());
        }
        return type.IsInt1Type() || type.IsInt8Type() ? 1 : WORD_BYTES;
    }

    static int integerConstant(IrValue value) {
        if (value instanceof IrConstantInt number) {
            return number.GetValue();
        }
        if (value instanceof IrConstantChar character) {
            return character.GetValue() & 0xff;
        }
        throw new IllegalArgumentException("Expected an integer constant: " + value);
    }

    private static int align(int size, int alignment) {
        return (size + alignment - 1) & -alignment;
    }
}
