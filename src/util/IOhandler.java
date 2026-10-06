package util;

import error.Error;
import error.ErrorRecorder;
import frontend.FrontEnd;
import frontend.ast.Node;
import frontend.lexer.Token;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;

public class IOhandler {
    private static PushbackInputStream input = null;
    private static FileOutputStream lexerOutputFile = null;
    private static FileOutputStream parserOutputFile = null;
    private static FileOutputStream errorFile = null;
    
    public static void setIO() throws IOException {
        input = new PushbackInputStream((new FileInputStream("testfile.txt")), 16);
        if (Setting.PRINT_LEXER) {
            IOhandler.lexerOutputFile = new FileOutputStream("lexer.txt");
        }
        if (Setting.PRINT_PARSER) {
            IOhandler.parserOutputFile = new FileOutputStream("parser.txt");
        }
        if (Setting.PRINT_ERROR) {
            IOhandler.errorFile = new FileOutputStream("error.txt");
        }
    }
    
    public static PushbackInputStream getInput() {
        return input;
    }
    
    public static void PrintTokenList() throws IOException {
        if (!Setting.PRINT_LEXER) {
            return;
        }
        ArrayList<Token> tokenList = FrontEnd.GetTokenList();
        for (Token token : tokenList) {
            String string = token.GetTokenType() + " " + token.GetStringValue() + "\n";
            lexerOutputFile.write(string.getBytes());
        }
    }
    
    public static void PrintAstTree() throws IOException {
        if (!Setting.PRINT_PARSER) {
            return;
        }
        Node astTree = FrontEnd.GetAstTree();
        parserOutputFile.write(astTree.toString().getBytes());
    }
    
    public static void PrintErrorMessage() throws IOException {
        if (!Setting.PRINT_ERROR) {
            return;
        }
        ArrayList<Error> errorList = ErrorRecorder.GetErrorList();
        for (Error error : errorList) {
            errorFile.write((error + "\n").getBytes());
        }
    }
}
