package utils;
import backend.BackEnd;
import error.Error;
import error.ErrorRecorder;
import frontend.FrontEnd;
import frontend.ast.Node;
import frontend.lexer.Token;
import midend.MidEnd;
import midend.llvm.IrModule;
import optimize.OptimizeManager;
import midend.symbol.SymbolTable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;

public class IOhandler {
    private static PushbackInputStream input = null;
    private static FileOutputStream lexerOutputFile = null;
    private static FileOutputStream parserOutputFile = null;
    private static FileOutputStream symbolOutputFile = null;
    private static FileOutputStream llvmInitOutputFile = null;
    private static FileOutputStream llvmPhiOutputFile = null;
    private static FileOutputStream llvmOutputFile = null;
    private static FileOutputStream errorFile = null;
    private static final Path MIPS_OUTPUT = Path.of("mips.txt");

    public static void setIO() throws IOException {
        input = new PushbackInputStream((new FileInputStream("testfile.txt")), 16);
        if (Setting.PRINT_MIPS) {
            Files.deleteIfExists(MIPS_OUTPUT);
        }
        for (String stage : new String[]{"前", "后"}) {
            for (String kind : new String[]{"中间", "目标"}) Files.deleteIfExists(artifactPath(stage, kind));
        }
        if (Setting.PRINT_LEXER) {
            IOhandler.lexerOutputFile = new FileOutputStream("lexer.txt");
        }
        if (Setting.PRINT_PARSER) {
            IOhandler.parserOutputFile = new FileOutputStream("parser.txt");
        }
        if (Setting.PRINT_SYMBOL) {
            IOhandler.symbolOutputFile = new FileOutputStream("symbol.txt");
        }
        if (Setting.PRINT_ERROR) {
            IOhandler.errorFile = new FileOutputStream("error.txt");
        }
        if (Setting.PRINT_LLVM) {
            IOhandler.llvmOutputFile = new FileOutputStream("llvm_ir.txt");
        }
    }

    public static PushbackInputStream GetInput() {
        return input;
    }

    public static void PrintTokenList() throws IOException {
        if (!Setting.PRINT_LEXER) {
            return;
        }
        ArrayList<Token> tokenList = FrontEnd.GetTokenList();
        for (Token token : tokenList) {
            String string = token.GetTokenType() + " " + token.GetStringValue() + "\n";
            lexerOutputFile.write(string.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void PrintAstTree() throws IOException {
        if (!Setting.PRINT_PARSER) {
            return;
        }
        Node astTree = FrontEnd.GetAstTree();
        parserOutputFile.write(astTree.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void PrintSymbolTable() throws IOException {
        if (!Setting.PRINT_SYMBOL) {
            return;
        }
        SymbolTable rootSymbolTable = MidEnd.GetrootSymbolTable();
        symbolOutputFile.write(rootSymbolTable.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void PrintLlvm() throws IOException {
        if (!Setting.PRINT_LLVM) {
            return;
        }
        IrModule irModule = MidEnd.GetIrModule();
        llvmOutputFile.write(irModule.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void PrintMips() throws IOException {
        if (Setting.PRINT_MIPS) {
            Files.writeString(MIPS_OUTPUT, BackEnd.GenerateMips(MidEnd.GetIrModule()),
                StandardCharsets.UTF_8);
        }
    }

    private static Path artifactPath(String stage, String kind) {
        try {
            return Path.of(Setting.ARTIFACT_PREFIX + "_优化" + stage + kind + "代码.txt");
        } catch (InvalidPathException exception) {
            // Linux's C locale can use ASCII for filenames even with UTF-8 file contents.
            String prefix = kind.equals("中间") ? "llvm_ir" : "mips";
            String suffix = stage.equals("前") ? "before" : "after";
            return Path.of(prefix + "_" + suffix + ".txt");
        }
    }

    public static void PrintOptimizedOutputs() throws IOException {
        IrModule module = MidEnd.GetIrModule();
        String beforeIr = module.toString();
        String beforeMips = Setting.PRINT_MIPS ? BackEnd.GenerateMips(module, false) : "";
        OptimizeManager.optimize(module);
        String afterIr = module.toString();
        String afterMips = Setting.PRINT_MIPS ? BackEnd.GenerateMips(module, true) : "";
        Files.writeString(artifactPath("前", "中间"), beforeIr, StandardCharsets.UTF_8);
        Files.writeString(artifactPath("后", "中间"), afterIr, StandardCharsets.UTF_8);
        if (Setting.PRINT_LLVM) {
            llvmOutputFile.write((Setting.FINE_TUNING ? afterIr : beforeIr).getBytes(StandardCharsets.UTF_8));
        }
        if (Setting.PRINT_MIPS) {
            Files.writeString(artifactPath("前", "目标"), beforeMips, StandardCharsets.UTF_8);
            Files.writeString(artifactPath("后", "目标"), afterMips, StandardCharsets.UTF_8);
            Files.writeString(MIPS_OUTPUT, Setting.FINE_TUNING ? afterMips : beforeMips, StandardCharsets.UTF_8);
        }
    }

    public static void PrintErrorMessage() throws IOException {
        if (!Setting.PRINT_ERROR) {
            return;
        }
        ArrayList<Error> errorList = ErrorRecorder.GetErrorList();
        for (Error error : errorList) {
            errorFile.write((error + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
