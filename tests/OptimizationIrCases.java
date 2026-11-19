import backend.BackEnd;
import midend.llvm.IrBuilder;
import midend.llvm.IrModule;
import midend.llvm.constant.*;
import midend.llvm.instr.*;
import midend.llvm.instr.io.*;
import midend.llvm.type.*;
import midend.llvm.value.*;
import java.nio.file.*;
import java.util.*;

/** IR-level boundaries that the surface language does not always generate. */
public class OptimizationIrCases {
    private static void print(IrValue value) {
        if (!value.GetIrType().IsInt32Type()) value = new ExtendInstr(value,IrBaseType.INT32);
        new PrintIntInstr(value);
        new PrintCharInstr(new IrConstantChar(32));
    }

    public static void main(String[] args) throws Exception {
        IrModule module = new IrModule();
        IrBuilder.SetCurrentModule(module);
        if (args[0].equals("escape")) {
            IrFunction writer = IrBuilder.GetNewFunctionIr("writer",IrBaseType.VOID);
            IrParameter pointer = new IrParameter(new IrPointerType(IrBaseType.INT32),"%pointer");
            writer.AddParameter(pointer);
            new StoreInstr(new IrConstantInt(9),pointer);
            new ReturnInstr(null);
            IrBuilder.GetNewFunctionIr("main",IrBaseType.INT32);
            AllocateInstr local = new AllocateInstr(IrBaseType.INT32);
            new StoreInstr(new IrConstantInt(7),local);
            print(new LoadInstr(local));
            new CallInstr(writer,new ArrayList<>(List.of(local)));
            print(new LoadInstr(local));
        } else {
            IrBuilder.GetNewFunctionIr("main",IrBaseType.INT32);
            print(new AluInstr("+",new IrConstantChar(255),new IrConstantChar(2)));
            print(new AluInstr("/",new IrConstantChar(253),new IrConstantChar(2)));
            print(new AluInstr("%",new IrConstantChar(253),new IrConstantChar(2)));
            print(new AluInstr("udiv",new IrConstantInt(-1),new IrConstantInt(2)));
            print(new AluInstr("urem",new IrConstantInt(-1),new IrConstantInt(2)));
            print(new TruncInstr(new IrConstantInt(511),IrBaseType.INT8));
            print(new TruncInstr(new IrConstantInt(2),IrBaseType.INT1));
            print(new CompareInstr("<",new IrConstantChar(255),new IrConstantChar(1)));
            print(new CompareInstr("ult",new IrConstantChar(255),new IrConstantChar(1)));
            print(new CompareInstr("ugt",new IrConstantInt(-1),new IrConstantInt(0)));
            AllocateInstr local = new AllocateInstr(IrBaseType.INT8);
            new StoreInstr(new IrConstantChar(254),local);
            print(new LoadInstr(local));
        }
        new ReturnInstr(new IrConstantInt(0));
        Files.writeString(Path.of("mips.txt"),BackEnd.GenerateMips(module));
        Files.writeString(Path.of("optimized.ll"),module.toString());
    }
}
