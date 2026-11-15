import error.ErrorRecorder;
import frontend.FrontEnd;
import midend.MidEnd;
import utils.IOhandler;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        IOhandler.setIO();
        FrontEnd.SetInput();
        FrontEnd.GenerateTokenList();
        FrontEnd.GenerateAstTree();
        MidEnd.GenerateSymbolTable();
        if (ErrorRecorder.HaveNoError()) {
            MidEnd.GenerateIr();
        }
        IOhandler.PrintTokenList();
        IOhandler.PrintAstTree();
        IOhandler.PrintSymbolTable();
        IOhandler.PrintErrorMessage();
        if (ErrorRecorder.HaveNoError()) {
            IOhandler.PrintLlvm();
            IOhandler.PrintMips();
        }
    }
}
