package backend;

import java.math.BigInteger;

/** Exact signed division by a compile-time constant, including negative operands. */
final class ConstantArithmetic {
    private ConstantArithmetic() { }

    static void multiply(MipsModule mips, int value) {
        if (value == 0) { mips.instruction("move", "$t0", "$zero"); return; }
        long magnitude = Math.abs((long) value);
        if ((magnitude & (magnitude - 1)) == 0) {
            int shift = Long.numberOfTrailingZeros(magnitude);
            if (shift != 0) mips.instruction("sll", "$t0", "$t0", Integer.toString(shift));
            if (value < 0) mips.instruction("subu", "$t0", "$zero", "$t0");
        } else if (!multiplySmall(mips, "$t0", "$t0", value)) {
            mips.immediate("$t1", value);
            mips.instruction("mul", "$t0", "$t0", "$t1");
        }
    }

    /** Reserve t1 as scratch; t2 must survive remainder reconstruction. */
    static boolean multiplySmall(MipsModule mips, String source, String result, int value) {
        if (System.getProperty("compiler.smallMultiply", "on").equalsIgnoreCase("off") ||
            value == 0 || value == 1 || value == -1) return false;
        // Compare coefficients modulo 2^32, just like LLVM i32 mul. Every
        // selected sequence uses only two ALU instructions (mul alone costs 3).
        for (int shift = 1; shift <= 31; shift++) {
            long power = 1L << shift;
            String operation;
            boolean reversed = false;
            if (value == (int)(power + 1)) operation = "addu";
            else if (value == (int)(power - 1)) operation = "subu";
            else if (value == (int)(1 - power)) { operation = "subu"; reversed = true; }
            else continue;
            mips.instruction("sll", "$t1", source, Integer.toString(shift));
            mips.instruction(operation, result, reversed ? source : "$t1", reversed ? "$t1" : source);
            return true;
        }
        return false;
    }

    static void divide(MipsModule mips, int divisor, boolean remainder) {
        if (remainder) mips.instruction("move", "$t2", "$t0");
        long magnitude = Math.abs((long) divisor);
        if (magnitude == 1) {
            if (divisor < 0) mips.instruction("subu", "$t0", "$zero", "$t0");
        } else if ((magnitude & (magnitude - 1)) == 0) {
            int shift = Long.numberOfTrailingZeros(magnitude);
            mips.instruction("sra", "$t1", "$t0", "31");
            mips.instruction("srl", "$t1", "$t1", Integer.toString(32 - shift));
            mips.instruction("addu", "$t0", "$t0", "$t1");
            mips.instruction("sra", "$t0", "$t0", Integer.toString(shift));
            if (divisor < 0) mips.instruction("subu", "$t0", "$zero", "$t0");
        } else {
            // Acc's multiplier bounds with 31 bits of signed precision.
            int shift = 64 - Long.numberOfLeadingZeros(magnitude - 1);
            BigInteger numerator = BigInteger.ONE.shiftLeft(32 + shift);
            BigInteger denominator = BigInteger.valueOf(magnitude);
            long low = numerator.divide(denominator).longValueExact();
            long high = numerator.add(BigInteger.ONE.shiftLeft(shift + 1)).divide(denominator).longValueExact();
            while (shift > 0 && (low >> 1) < (high >> 1)) {
                low >>= 1;
                high >>= 1;
                shift--;
            }
            mips.immediate("$t1", (int) high);
            mips.instruction("mult", "$t0", "$t1");
            mips.instruction("mfhi", "$t1");
            if (high >= (1L << 31)) mips.instruction("addu", "$t1", "$t1", "$t0");
            if (shift > 0) mips.instruction("sra", "$t1", "$t1", Integer.toString(shift));
            mips.instruction("srl", "$t0", "$t0", "31");
            mips.instruction("addu", "$t0", "$t1", "$t0");
            if (divisor < 0) mips.instruction("subu", "$t0", "$zero", "$t0");
        }
        if (remainder) {
            multiply(mips, divisor);
            mips.instruction("subu", "$t0", "$t2", "$t0");
        }
    }
}
