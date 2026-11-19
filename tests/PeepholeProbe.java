package backend;
import java.nio.file.*;

/** The accepted compiler has no peephole pass; use identity there for comparison. */
public class PeepholeProbe {
    public static void main(String[] args) throws Exception {
        StringBuilder text = new StringBuilder(Files.readString(Path.of("input.s")));
        try {
            Class<?> pass = Class.forName("backend.LocalPeephole");
            pass.getDeclaredMethod("run", StringBuilder.class).invoke(null, text);
        } catch (ClassNotFoundException ignored) { }
        Files.writeString(Path.of("mips.txt"), text.toString());
    }
}
