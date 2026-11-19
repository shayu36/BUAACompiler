package backend;

import java.util.*;
import java.util.regex.Pattern;

/** Straight-line register facts; all control-flow boundaries discard the facts. */
final class LocalPeephole {
    private static final Pattern REGISTER = Pattern.compile("\\$(zero|v[01]|a[0-3]|t[0-9]|s[0-7]|sp|gp|ra)");
    private static final Pattern HOME = Pattern.compile("-?\\d+\\(\\$(sp|gp)\\)");
    private static final Set<String> DEFINES_FIRST = Set.of(
        "addu", "addiu", "subu", "mul", "slt", "sltu", "slti", "sltiu", "seq", "sne",
        "sll", "srl", "sra", "sllv", "srlv", "srav", "and", "andi", "or", "ori",
        "xor", "xori", "nor", "lui", "la", "mfhi", "mflo", "lbu", "lb", "lh", "lhu");
    private record Constant(int bits) { }
    /** The value has no set bits at or above this bit count. */
    private record Bounded(Object identity, int bits) { }
    private final Map<String, Object> registers = new LinkedHashMap<>();
    private final Map<String, Object> memory = new HashMap<>();

    static void run(StringBuilder text) {
        LocalPeephole pass = new LocalPeephole();
        StringBuilder result = new StringBuilder(text.length());
        for (String line : text.toString().split("\n")) {
            String replacement = pass.rewrite(line);
            if (replacement != null) result.append(replacement).append('\n');
        }
        text.setLength(0);
        text.append(result);
    }

    private Object value(String register) {
        if (register.equals("$zero")) return new Constant(0);
        return registers.computeIfAbsent(register, ignored -> new Object());
    }

    private void define(String register, Object value) {
        if (register.equals("$zero")) return;
        // Stack/global-base changes invalidate every address fact, even if the
        // numeric displacement happens to be unchanged.
        if (register.equals("$sp") || register.equals("$gp")) memory.clear();
        registers.put(register, value);
    }

    private String holder(Object value) {
        if (value.equals(new Constant(0))) return "$zero";
        for (var entry : registers.entrySet()) {
            // Never substitute an address base for a data register.
            if (!entry.getKey().equals("$sp") && !entry.getKey().equals("$gp") &&
                entry.getValue().equals(value)) return entry.getKey();
        }
        return null;
    }

    private void remember(String address, Object value) {
        if (memory.size() >= 128) memory.clear();
        memory.put(address, value);
    }

    private static int width(Object value) {
        if (value instanceof Constant constant) return 32 - Integer.numberOfLeadingZeros(constant.bits());
        return value instanceof Bounded bounded ? bounded.bits() : 32;
    }

    private String rewrite(String original) {
        String code = original.split("#", 2)[0].trim();
        if (code.isEmpty() || code.equals("nop")) return original;
        int split = code.indexOf(' ');
        String op = split < 0 ? code : code.substring(0, split);
        String[] args = split < 0 ? new String[0] : code.substring(split + 1).trim().split("\\s*,\\s*");
        if (op.equals("li") && args.length == 2 && REGISTER.matcher(args[0]).matches()) {
            try {
                Object constant = new Constant((int) Long.decode(args[1]).longValue());
                if (value(args[0]).equals(constant)) return null;
                define(args[0], constant);
                return original;
            } catch (NumberFormatException ignored) { }
        }
        if (op.equals("move") && args.length == 2 && REGISTER.matcher(args[0]).matches() &&
            REGISTER.matcher(args[1]).matches()) {
            Object source = value(args[1]);
            if (value(args[0]).equals(source)) return null;
            define(args[0], source);
            return original;
        }
        if (op.equals("andi") && args.length == 3 && REGISTER.matcher(args[0]).matches() &&
            REGISTER.matcher(args[1]).matches()) {
            try {
                int mask = Integer.decode(args[2]);
                if (mask >= 0 && mask <= 65535) {
                    Object source = value(args[1]);
                    long possibleBits = (1L << width(source)) - 1;
                    if ((mask & possibleBits) == possibleBits) {
                        if (value(args[0]).equals(source)) return null;
                        define(args[0], source);
                        return "    move " + args[0] + ", " + args[1];
                    }
                    define(args[0], mask == 0 ? new Constant(0) :
                        new Bounded(new Object(), 32 - Integer.numberOfLeadingZeros(mask)));
                    return original;
                }
            } catch (NumberFormatException ignored) { }
        }
        if (op.equals("sw") && args.length == 2 && REGISTER.matcher(args[0]).matches()) {
            Object source = value(args[0]);
            boolean home = HOME.matcher(args[1]).matches();
            if (home && source.equals(memory.get(args[1]))) return null;
            // Any write may alias a previously known word, including pointer
            // stores, partial overlaps and stack/global addresses passed around.
            memory.clear();
            if (home) remember(args[1], source);
            return original;
        }
        if (op.equals("lw") && args.length == 2 && REGISTER.matcher(args[0]).matches() &&
            !args[0].equals("$sp") && !args[0].equals("$gp")) {
            boolean home = HOME.matcher(args[1]).matches();
            Object known = home ? memory.get(args[1]) : null;
            if (known != null) {
                if (value(args[0]).equals(known)) return null;
                String source = holder(known);
                if (source != null) {
                    define(args[0], known);
                    return "    move " + args[0] + ", " + source;
                }
            }
            Object loaded = known != null ? known : new Object();
            define(args[0], loaded);
            if (home && !args[0].equals("$zero")) remember(args[1], loaded);
            return original;
        }
        if (DEFINES_FIRST.contains(op) && args.length > 0 && REGISTER.matcher(args[0]).matches()) {
            int bits = switch (op) {
                case "lbu" -> 8;
                case "lhu" -> 16;
                case "slt", "sltu", "slti", "sltiu", "seq", "sne" -> 1;
                default -> 32;
            };
            define(args[0], bits == 32 ? new Object() : new Bounded(new Object(), bits));
            return original;
        }
        // Labels, branches, calls, syscalls, byte stores and every unrecognized
        // instruction are barriers. Keep all delay-slot nops in place.
        registers.clear();
        memory.clear();
        return original;
    }
}
