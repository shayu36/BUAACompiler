import backend.BackEnd;
import midend.llvm.IrBuilder;
import midend.llvm.IrModule;
import midend.llvm.constant.*;
import midend.llvm.instr.*;
import midend.llvm.instr.io.*;
import midend.llvm.type.*;
import midend.llvm.value.*;
import java.nio.file.*;

/** Signed narrow operands must survive conversion and I/O without corruption. */
public class RegisterAllocationIrCases {
    private static void print(IrValue value) {
        if (!value.GetIrType().IsInt32Type()) value = new ExtendInstr(value,IrBaseType.INT32);
        new PrintIntInstr(value);
        new PrintCharInstr(new IrConstantChar(32));
    }

    public static void main(String[] args) throws Exception {
        IrModule module = new IrModule();
        IrBuilder.SetCurrentModule(module);
        IrBuilder.GetNewFunctionIr("main",IrBaseType.INT32);
        IrValue original = new GetIntInstr();
        IrValue byteValue = new TruncInstr(original,IrBaseType.INT8);
        IrValue narrowDiv = new AluInstr("/",byteValue,new IrConstantChar(2));
        IrValue signedCompare = new CompareInstr("<",byteValue,new IrConstantChar(1));
        IrValue unsignedCompare = new CompareInstr("ult",byteValue,new IrConstantChar(1));
        print(original);
        print(byteValue);
        print(narrowDiv);
        print(signedCompare);
        print(unsignedCompare);
        print(original);
        print(byteValue);
        new ReturnInstr(new IrConstantInt(0));
        Files.writeString(Path.of("mips.txt"),BackEnd.GenerateMips(module));
        Files.writeString(Path.of("optimized.ll"),module.toString());
    }
}
