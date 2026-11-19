"""Verify course MARS configuration without relying on documentation defaults."""
from verify_phase1 import ROOT, FLAGS, MARS, run
folder=ROOT/'tests/.phase3-work/delay-probe'
folder.mkdir(parents=True,exist_ok=True)
(folder/'mips.txt').write_text('''.text
.globl main
main:
    li $a0, 1
    beq $zero, $zero, print_value
    li $a0, 2
print_value:
    li $v0, 1
    syscall
    li $v0, 10
    syscall
''',encoding='ascii')
r=run(['java',*FLAGS,'-jar',MARS,'mips.txt'],folder)
actual=r.stdout.decode().split('\n',1)[1].strip()
assert actual=='1',(actual,r.stderr)
print('Course MARS: delay slots disabled (sentinel output 1)')
