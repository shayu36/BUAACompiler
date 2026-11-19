package optimize;

import midend.llvm.constant.*;
import midend.llvm.instr.*;
import midend.llvm.type.IrType;
import midend.llvm.value.*;
import java.util.*;

public final class IrSupport {
    private IrSupport() { }

    public static boolean isNumber(IrValue value) {
        return value instanceof IrConstantInt || value instanceof IrConstantChar;
    }

    public static int number(IrValue value) {
        return Integer.parseInt(value.GetIrName());
    }

    public static int signedNumber(IrValue value) {
        int n = number(value);
        if (value.GetIrType().IsInt8Type()) return (byte) n;
        if (value.GetIrType().IsInt1Type()) return -(n & 1);
        return n;
    }

    public static IrConstantInt constant(int value, IrType type) {
        if (type.IsInt8Type()) value &= 255;
        if (type.IsInt1Type()) value &= 1;
        return new IrConstantInt(value, type);
    }

    public static void detach(Instr instr) {
        for (IrValue value : instr.GetUseValueList()) {
            if (value != null) value.DeleteUser(instr);
        }
    }

    public static Set<IrValue> scalarSlots(IrFunction function) {
        Set<IrValue> slots = new LinkedHashSet<>();
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                if (instr instanceof AllocateInstr allocation && !allocation.GetTargetType().IsArrayType()) {
                    slots.add(instr);
                }
            }
        }
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            for (Instr instr : block.GetInstrList()) {
                List<IrValue> args = instr.GetUseValueList();
                for (int i = 0; i < args.size(); i++) {
                    boolean memoryPointer = (instr instanceof LoadInstr && i == 0) ||
                        (instr instanceof StoreInstr && i == 1);
                    if (!memoryPointer) slots.remove(args.get(i));
                }
            }
        }
        return slots;
    }

    public static boolean isCall(Instr instr) {
        return instr.GetInstrType() == InstrType.CALL || instr.GetInstrType() == InstrType.IO;
    }

    public static boolean isPure(Instr instr) {
        return switch (instr.GetInstrType()) {
            case ALU, CMP, GEP, EXTEND, TRUNC -> true;
            default -> false;
        };
    }
}
