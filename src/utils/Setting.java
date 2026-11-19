package utils;

public class Setting {
    // java -Dcompiler.optimize=off Compiler selects the baseline target.
    public static final boolean FINE_TUNING = !java.util.Set.of("off", "false", "0")
        .contains(System.getProperty("compiler.optimize", "on").toLowerCase(java.util.Locale.ROOT));
    public static final String ARTIFACT_PREFIX = System.getProperty("compiler.case", "testfile14") +
        System.getProperty("compiler.studentId", "24373313") + System.getProperty("compiler.studentName", "贾鑫洋");
    public static final boolean PRINT_LEXER = false;
    public static final boolean PRINT_PARSER = true;
    public static final boolean PRINT_SYMBOL = true;
    public static final boolean PRINT_ERROR = true;
    public static final boolean PRINT_LLVM = true;
    public static final boolean PRINT_MIPS = true;
}
