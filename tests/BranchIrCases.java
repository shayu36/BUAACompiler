import backend.BackEnd;
import midend.llvm.*;
import midend.llvm.constant.*;
import midend.llvm.instr.*;
import midend.llvm.instr.io.*;
import midend.llvm.type.*;
import midend.llvm.value.*;
import java.nio.file.*;

/** Exercise lowering directly, retaining narrow/unsigned predicates and distant edges. */
public class BranchIrCases {
    public static void main(String[] args) throws Exception {
        IrModule module = new IrModule();
        IrBuilder.SetCurrentModule(module);
        IrBuilder.GetNewFunctionIr("main", IrBaseType.INT32);
        IrValue a = new GetIntInstr(), b = new GetIntInstr();
        int width = Integer.parseInt(args[0]);
        String mode = args[1];
        if (width != 32) {
            IrType type = width == 8 ? IrBaseType.INT8 : IrBaseType.INT1;
            a = new TruncInstr(a, type); b = new TruncInstr(b, type);
        }
        for (String op : mode.equals("far") ? new String[]{">="} :
                new String[]{"<", "<=", ">", ">=", "ult", "ule", "ugt", "uge", "==", "!="}) {
            IrBasicBlock first = IrBuilder.GetNewBasicBlockIr();
            IrBasicBlock second = IrBuilder.GetNewBasicBlockIr();
            IrBasicBlock yes = mode.equals("falsefirst") ? second : first;
            IrBasicBlock no = mode.equals("falsefirst") ? first : second;
            IrBasicBlock join = IrBuilder.GetNewBasicBlockIr();
            IrValue cmp = new CompareInstr(op, a, b);
            IrValue intervening = mode.equals("nonadjacent") ? new AluInstr("+", a, b) : null;
            new BranchInstr(cmp, yes, no);
            IrBuilder.SetCurrentBasicBlock(yes);
            new PrintIntInstr(new IrConstantInt(1));
            if (mode.equals("shared")) new PrintIntInstr(new ExtendInstr(cmp, IrBaseType.INT32));
            if (intervening != null) new PrintIntInstr(intervening);
            new JumpInstr(join);
            IrBuilder.SetCurrentBasicBlock(no);
            new PrintIntInstr(new IrConstantInt(0));
            if (mode.equals("shared")) new PrintIntInstr(new ExtendInstr(cmp, IrBaseType.INT32));
            if (intervening != null) new PrintIntInstr(intervening);
            new JumpInstr(join);
            if (mode.equals("far")) {
                IrBasicBlock padding = IrBuilder.GetNewBasicBlockIr();
                var blocks = IrBuilder.GetCurFunctionIr().GetBasicBlocks();
                blocks.remove(padding); blocks.add(blocks.indexOf(no), padding);
                IrBuilder.SetCurrentBasicBlock(padding);
                for (int i = 0; i < 12000; i++) new PrintIntInstr(new IrConstantInt(12345));
                new JumpInstr(join);
            }
            IrBuilder.SetCurrentBasicBlock(join);
            new PrintCharInstr(new IrConstantChar(32));
        }
        new ReturnInstr(new IrConstantInt(0));
        Files.writeString(Path.of("optimized.ll"), module.toString());
        Files.writeString(Path.of("mips.txt"), BackEnd.GenerateMips(module, true));
    }
}
