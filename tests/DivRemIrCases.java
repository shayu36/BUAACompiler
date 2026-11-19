import backend.BackEnd;
import midend.llvm.*;
import midend.llvm.constant.*;
import midend.llvm.instr.*;
import midend.llvm.instr.io.*;
import midend.llvm.type.*;
import midend.llvm.value.*;
import optimize.OptimizeManager;
import java.nio.file.*;

/** Signed and unsigned divisions of the same bit patterns must have distinct keys. */
public class DivRemIrCases {
    public static void main(String[] args) throws Exception {
        IrModule module = new IrModule();
        IrBuilder.SetCurrentModule(module);
        IrBuilder.GetNewFunctionIr("main",IrBaseType.INT32);
        IrValue a = new GetIntInstr(), b = new GetIntInstr();
        boolean signed = args[0].equals("signed");
        IrValue q = new AluInstr(signed ? "/" : "udiv",a,b);
        IrValue otherR = new AluInstr(signed ? "urem" : "%",a,b);
        IrValue ownR = new AluInstr(signed ? "%" : "urem",a,b);
        new PrintIntInstr(q); new PrintCharInstr(new IrConstantChar(32));
        new PrintIntInstr(otherR); new PrintCharInstr(new IrConstantChar(32));
        new PrintIntInstr(ownR);
        new ReturnInstr(new IrConstantInt(0));
        OptimizeManager.optimize(module);
        Files.writeString(Path.of("optimized.ll"),module.toString());
        Files.writeString(Path.of("mips.txt"),BackEnd.GenerateMips(module,true));
    }
}
