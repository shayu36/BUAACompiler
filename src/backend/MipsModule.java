package backend;

final class MipsModule {
    final StringBuilder data = new StringBuilder(".data\n");
    final StringBuilder text = new StringBuilder(".text\n");

    void label(String name) {
        text.append(name).append(":\n");
    }

    void instruction(String operation, String... operands) {
        text.append("    ").append(operation);
        if (operands.length > 0) {
            text.append(' ').append(String.join(", ", operands));
        }
        text.append('\n');
    }

    void transfer(String operation, String... operands) {
        instruction(operation, operands);
        instruction("nop");
    }

    void immediate(String register, int value) {
        instruction("li", register, Integer.toString(value));
    }

    // $t3 is reserved for offsets outside the signed 16-bit immediate range.
    void addOffset(String target, String base, int offset) {
        if (offset >= Short.MIN_VALUE && offset <= Short.MAX_VALUE) {
            instruction("addiu", target, base, Integer.toString(offset));
        } else {
            immediate("$t3", offset);
            instruction("addu", target, base, "$t3");
        }
    }

    void memory(String operation, String register, String base, int offset) {
        if (offset < Short.MIN_VALUE || offset > Short.MAX_VALUE) {
            addOffset("$t3", base, offset);
            base = "$t3";
            offset = 0;
        }
        instruction(operation, register, offset + "(" + base + ")");
    }

    @Override
    public String toString() {
        return data.toString() + "\n" + text;
    }
}
