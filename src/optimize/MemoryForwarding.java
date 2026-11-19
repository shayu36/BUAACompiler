package optimize;

import midend.llvm.instr.*;
import midend.llvm.type.IrArrayType;
import midend.llvm.type.IrPointerType;
import midend.llvm.type.IrType;
import midend.llvm.value.*;
import java.util.*;

/** Block-local memory values, invalidated by every potentially aliasing write. */
final class MemoryForwarding {
    private static final int MAX_FACTS = 128;
    private record Address(IrValue root, Integer offset, IrValue symbolic) { }
    private record Fact(Address address, IrValue value, IrType type, int width) { }

    static void run(IrFunction function) {
        if (System.getProperty("compiler.memoryForward","on").equalsIgnoreCase("off")) return;
        Set<IrValue> privateSlots = IrSupport.scalarSlots(function);
        for (IrBasicBlock block : function.GetBasicBlocks()) {
            Map<Address,Fact> facts = new LinkedHashMap<>();
            for (Iterator<Instr> it = block.GetInstrList().iterator(); it.hasNext();) {
                Instr instr = it.next();
                List<IrValue> args = instr.GetUseValueList();
                if (instr instanceof GepInstr && IrSupport.isNumber(args.get(1)) && IrSupport.number(args.get(1)) == 0 &&
                    !((IrPointerType)args.get(0).GetIrType()).getTargetType().IsArrayType()) {
                    // An element pointer plus zero preserves its type. Canonicalize
                    // it before forwarding so a redundant load cannot keep a more
                    // expensive zero-offset address calculation alive.
                    instr.ModifyAllUsersToNewValue(args.get(0));
                    IrSupport.detach(instr);
                    it.remove();
                } else if (IrSupport.isCall(instr)) {
                    // Includes input routines that write through pointers. No
                    // interprocedural memory assumptions are needed by this pass.
                    facts.clear();
                } else if (instr instanceof StoreInstr) {
                    IrValue value = args.get(0);
                    IrType type = value.GetIrType();
                    if (!scalar(type)) { facts.clear(); continue; }
                    Address address = address(args.get(1));
                    int width = bytes(type);
                    facts.values().removeIf(f -> overlaps(address,width,f.address,f.width,privateSlots));
                    remember(facts,new Fact(address,value,type,width));
                } else if (instr instanceof LoadInstr && scalar(instr.GetIrType())) {
                    Address address = address(args.get(0));
                    Fact previous = facts.get(address);
                    if (previous != null && previous.type.equals(instr.GetIrType())) {
                        instr.ModifyAllUsersToNewValue(previous.value);
                        IrSupport.detach(instr);
                        it.remove();
                    } else remember(facts,new Fact(address,instr,instr.GetIrType(),bytes(instr.GetIrType())));
                }
            }
        }
    }

    private static void remember(Map<Address,Fact> facts, Fact fact) {
        facts.put(fact.address,fact);
        if (facts.size() > MAX_FACTS) facts.remove(facts.keySet().iterator().next());
    }

    private static boolean scalar(IrType type) {
        return type.IsInt32Type() || type.IsInt8Type() || type.IsInt1Type();
    }

    private static int bytes(IrType type) {
        if (type instanceof IrArrayType array) return array.GetArraySize()*bytes(array.GetElementType());
        return type.IsInt8Type() || type.IsInt1Type() ? 1 : 4;
    }

    private static Address address(IrValue pointer) {
        IrValue root = pointer;
        int offset = 0;
        boolean known = true;
        // GEP uses 32-bit target arithmetic; offset accumulation wraps identically.
        while (root instanceof GepInstr gep) {
            IrValue base = gep.GetUseValueList().get(0), index = gep.GetUseValueList().get(1);
            if (IrSupport.isNumber(index)) offset += IrSupport.signedNumber(index)*bytes(GepInstr.GetTargetType(base));
            else known = false;
            root = base;
        }
        return new Address(root,known ? offset : null,known ? null : pointer);
    }

    private static boolean object(IrValue value) {
        return value instanceof AllocateInstr || value instanceof IrGlobalValue;
    }

    private static boolean overlaps(Address a, int aWidth, Address b, int bWidth, Set<IrValue> privateSlots) {
        if (a.root != b.root) {
            // Distinct real objects and nonescaping stack slots cannot alias.
            // Different array parameters, loaded pointers and unknown roots can.
            if (object(a.root) && object(b.root) || privateSlots.contains(a.root) || privateSlots.contains(b.root)) return false;
            return true;
        }
        if (a.offset == null || b.offset == null) return true;
        // Scalar accesses occupy at most four bytes. Check each byte so intervals
        // crossing the 32-bit address boundary are also treated conservatively.
        for (int i = 0; i < aWidth; i++) for (int j = 0; j < bWidth; j++)
            if (a.offset+i == b.offset+j) return true;
        return false;
    }
}
