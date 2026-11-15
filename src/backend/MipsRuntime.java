package backend;

import java.util.EnumSet;

final class MipsRuntime {
    enum Builtin {
        GET_CHAR("get_char"), GET_INT("get_int"), GET_STRING("get_string"),
        PUT_CHAR("put_char"), PUT_INT("put_int"), PUT_STRING("put_string");

        final String label;

        Builtin(String name) {
            label = "__sysy_" + name;
        }

        static Builtin forFunction(String name) {
            return switch (name) {
                case "@get_char" -> GET_CHAR;
                case "@get_int" -> GET_INT;
                case "@get_string" -> GET_STRING;
                case "@put_char" -> PUT_CHAR;
                case "@put_int" -> PUT_INT;
                case "@put_string", "@put_str" -> PUT_STRING;
                default -> null;
            };
        }
    }

    private final EnumSet<Builtin> used = EnumSet.noneOf(Builtin.class);

    void call(MipsModule mips, Builtin builtin) {
        if (builtin == Builtin.PUT_INT) {
            mips.immediate("$v0", 1);
            mips.instruction("syscall");
        } else {
            used.add(builtin);
            mips.transfer("jal", builtin.label);
        }
    }

    void emit(MipsModule mips) {
        if (used.contains(Builtin.GET_CHAR) || used.contains(Builtin.GET_INT) ||
            used.contains(Builtin.GET_STRING)) {
            mips.data.append("""
                .align 2
                __sysy_pending_byte: .word -1
                __sysy_input_byte: .space 1
                """);
            // All input uses the same byte stream, including when calls are mixed.
            mips.text.append("""
                __sysy_get_char:
                    lw $v0, __sysy_pending_byte
                    bltz $v0, __sysy_read_stdin
                    nop
                    li $a0, -1
                    sw $a0, __sysy_pending_byte
                    jr $ra
                    nop
                __sysy_read_stdin:
                    li $v0, 14
                    move $a0, $zero
                    la $a1, __sysy_input_byte
                    li $a2, 1
                    syscall
                    blez $v0, __sysy_input_eof
                    nop
                    lbu $v0, __sysy_input_byte
                    jr $ra
                    nop
                __sysy_input_eof:
                    li $v0, -1
                    jr $ra
                    nop
                """);
        }
        if (used.contains(Builtin.GET_INT)) {
            // Match fgets with the library's 512-byte buffer, including space for its NUL.
            // Accumulate modulo 2^32 so INT_MIN works.
            mips.text.append("""
                __sysy_get_int:
                    move $t8, $ra
                    move $t4, $zero
                    li $t5, 1
                    li $t6, 10
                    li $t2, 511
                __sysy_int_skip_space:
                    jal __sysy_int_read_byte
                    nop
                    blez $v0, __sysy_int_done
                    nop
                    beq $v0, $t6, __sysy_int_done
                    nop
                    slti $t7, $v0, 33
                    bne $t7, $zero, __sysy_int_skip_space
                    nop
                    li $t7, 45
                    bne $v0, $t7, __sysy_int_plus
                    nop
                    li $t5, -1
                    j __sysy_int_next
                    nop
                __sysy_int_plus:
                    li $t7, 43
                    bne $v0, $t7, __sysy_int_digit
                    nop
                __sysy_int_next:
                    jal __sysy_int_read_byte
                    nop
                __sysy_int_digit:
                    addiu $t7, $v0, -48
                    sltiu $t9, $t7, 10
                    beq $t9, $zero, __sysy_int_consume_line
                    nop
                    mul $t4, $t4, $t6
                    addu $t4, $t4, $t7
                    j __sysy_int_next
                    nop
                __sysy_int_consume_line:
                    bltz $v0, __sysy_int_done
                    nop
                    beq $v0, $t6, __sysy_int_done
                    nop
                    jal __sysy_int_read_byte
                    nop
                    j __sysy_int_consume_line
                    nop
                __sysy_int_done:
                    mul $v0, $t4, $t5
                    jr $t8
                    nop
                __sysy_int_read_byte:
                    beq $t2, $zero, __sysy_int_buffer_end
                    nop
                    addiu $t2, $t2, -1
                    j __sysy_get_char
                    nop
                __sysy_int_buffer_end:
                    li $v0, -1
                    jr $ra
                    nop
                """);
        }
        if (used.contains(Builtin.GET_STRING)) {
            // Consume the line even when the destination is full, leaving room for the NUL.
            mips.text.append("""
                __sysy_get_string:
                    move $t8, $ra
                    move $t4, $a0
                    move $t5, $a1
                    li $t6, 10
                __sysy_string_read:
                    jal __sysy_get_char
                    nop
                    bltz $v0, __sysy_string_finish
                    nop
                    beq $v0, $t6, __sysy_string_finish
                    nop
                    li $t7, 13
                    beq $v0, $t7, __sysy_string_cr
                    nop
                    slti $t7, $t5, 2
                    bne $t7, $zero, __sysy_string_read
                    nop
                    sb $v0, 0($t4)
                    addiu $t4, $t4, 1
                    addiu $t5, $t5, -1
                    j __sysy_string_read
                    nop
                __sysy_string_cr:
                    jal __sysy_get_char
                    nop
                    beq $v0, $t6, __sysy_string_finish
                    nop
                    sw $v0, __sysy_pending_byte
                __sysy_string_finish:
                    blez $t5, __sysy_string_done
                    nop
                    slti $t7, $t5, 2
                    bne $t7, $zero, __sysy_string_terminate
                    nop
                    sb $t6, 0($t4)
                    addiu $t4, $t4, 1
                __sysy_string_terminate:
                    sb $zero, 0($t4)
                __sysy_string_done:
                    jr $t8
                    nop
                """);
        }
        if (used.contains(Builtin.PUT_CHAR)) {
            mips.data.append("__sysy_output_byte: .space 1\n");
            mips.text.append("""
                __sysy_put_char:
                    sb $a0, __sysy_output_byte
                    la $a1, __sysy_output_byte
                    li $a2, 1
                    li $a0, 1
                    li $v0, 15
                    syscall
                    jr $ra
                    nop
                """);
        }
        if (used.contains(Builtin.PUT_STRING)) {
            // Use the raw stdout syscall to preserve the bytes in char arrays.
            mips.text.append("""
                __sysy_put_string:
                    move $a1, $a0
                    move $t4, $a0
                __sysy_put_string_length:
                    lbu $t5, 0($t4)
                    beq $t5, $zero, __sysy_put_string_write
                    nop
                    addiu $t4, $t4, 1
                    j __sysy_put_string_length
                    nop
                __sysy_put_string_write:
                    subu $a2, $t4, $a1
                    li $a0, 1
                    li $v0, 15
                    syscall
                    jr $ra
                    nop
                """);
        }
    }
}
