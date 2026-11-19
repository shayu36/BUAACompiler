"""Run hand-built alias/clobber/control-flow cases through real MARS interpreters."""
import argparse
import concurrent.futures
import json
from pathlib import Path
from verify_phase1 import ROOT, FLAGS, MARS, run

p=argparse.ArgumentParser()
p.add_argument('--work',type=Path,default=ROOT/'tests/.final-8df36c8d')
p.add_argument('--label',default='final')
args=p.parse_args()
work=args.work
# body, expected stdout, optional input, optional helper after main's exit
cases={
 'byte_bound':('li $t0, 511\nsw $t0, 0($sp)\nlbu $t1, 0($sp)\nandi $t1, $t1, 255\nandi $a0, $t1, 255','255','',''),
 'signed_byte_bound':('li $t0, 511\nsw $t0, 0($sp)\nlb $t1, 0($sp)\nandi $a0, $t1, 255','255','',''),
 'noncontiguous_mask':('li $t0, 171\nsw $t0, 0($sp)\nlbu $t1, 0($sp)\nandi $a0, $t1, 240','160','',''),
 'boolean_bound':('li $t0, 4\nli $t1, 6\nslt $t2, $t0, $t1\nandi $t2, $t2, 1\nmove $a0, $t2','1','',''),
 'chained_masks':('li $t0, -1\nandi $t1, $t0, 255\nandi $t1, $t1, 255\nandi $a0, $t1, 15','15','',''),
 'halfword_bound':('li $t0, 131071\nsw $t0, 0($sp)\nlhu $t1, 0($sp)\nandi $t1, $t1, 65535\nmove $a0, $t1','65535','',''),
 'forward':('li $t0, 42\nsw $t0, 0($sp)\nlw $t1, 0($sp)\nlw $t2, 0($sp)\nli $t0, 42\nmove $t1, $t0\nsw $t2, 0($sp)\nlw $a0, 0($sp)','42','',''),
 'source_overwrite':('li $t0, 11\nsw $t0, 0($sp)\nli $t0, 99\nlw $a0, 0($sp)','11','',''),
 'byte_alias':('li $t0, 256\nsw $t0, 0($sp)\nli $t1, 7\nsb $t1, 0($sp)\nlw $a0, 0($sp)','263','',''),
 'pointer_alias':('li $t0, 11\nsw $t0, 0($sp)\nmove $t2, $sp\nli $t1, 77\nsw $t1, 0($t2)\nlw $a0, 0($sp)','77','',''),
 'stack_change':('li $t0, 11\nsw $t0, 0($sp)\naddiu $sp, $sp, -16\nli $t0, 77\nsw $t0, 0($sp)\naddiu $sp, $sp, 16\nlw $a0, 0($sp)','11','',''),
 'global_base_change':('li $t0, 14\nsw $t0, 0($gp)\naddiu $gp, $gp, 4\nlw $a0, 0($gp)','22','',''),
 'branch_join':('li $t0, 33\nsw $t0, 0($sp)\nbeq $zero, $zero, join\nnop\nli $t0, 99\njoin:\nlw $a0, 0($sp)','33','',''),
 'callee_clobber':('li $t0, 11\nsw $t0, 0($sp)\njal helper\nnop\nlw $a0, 0($sp)','77','',
                   'helper:\nli $t0, 77\nsw $t0, 0($sp)\njr $ra\nnop\n'),
 'syscall_clobber':('li $v0, 5\nsyscall\nli $v0, 5\nsyscall\nmove $a0, $v0','64','72\n64\n',''),
 'zero_store':('sw $zero, 0($sp)\nlw $t0, 0($sp)\nli $t0, 0\nlw $a0, 0($sp)','0','',''),
 'wide_constant':('li $t0, -2147483648\nli $t1, -2147483648\nsw $t0, 0($sp)\nlw $a0, 0($sp)\nli $t1, -2147483648','-2147483648','',''),
 'load_after_arithmetic':('li $t0, 37\nsw $t0, 0($gp)\nlw $t1, 0($gp)\naddiu $t1, $t1, 9\nlw $a0, 0($gp)','37','',''),
 'mixed_width_read':('li $t0, 511\nsw $t0, 0($sp)\nlbu $t0, 0($sp)\nlw $a0, 0($sp)','511','',''),
 'hilo_clobber':('li $t0, 11\nli $t1, 9\nmult $t0, $t1\nmflo $t0\nsw $t0, 0($sp)\nlw $a0, 0($sp)','99','',''),
}
classes={}
for version in ['baseline','candidate']:
 classes[version]=work/args.label/(version+'-classes')
 run(['javac','--release','17','-encoding','UTF-8','-cp',classes[version],'-d',classes[version],ROOT/'tests/PeepholeProbe.java'],ROOT)

def check(job):
 name,version,delay=job
 body,expected,data,helper=cases[name]
 folder=work/'machine'/version/f'{name}_{delay}'
 folder.mkdir(parents=True,exist_ok=True)
 assembly='.data\nbuf: .word 11, 22\n.text\n.globl main\nmain:\naddiu $sp, $sp, -64\nla $gp, buf\n'+body+'\nli $v0, 1\nsyscall\naddiu $sp, $sp, 64\nli $v0, 10\nsyscall\n'+helper
 (folder/'input.s').write_text(assembly)
 run(['java',*FLAGS,'-cp',classes[version],'backend.PeepholeProbe'],folder)
 command=['java',*FLAGS,'-jar']
 command+=([Path('D:/Accepted/tools/MARS-2024.jar'),'nc','me','ae91','se92','db','sm','mips.txt'] if delay else [MARS,'mips.txt'])
 r=run(command,folder,data.encode())
 actual=r.stdout.decode() if delay else r.stdout.decode().split('\n',1)[1]
 assert actual.strip()==expected,(name,version,delay,actual,expected)
 assert not r.stderr.strip(),r.stderr
 cycles=None if delay else json.loads((folder/'instruction_statistics.json').read_text())['finalCycles']
 return dict(case=name,version=version,delay=delay,passed=True,cycles=cycles)

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 records=list(pool.map(check,[(n,v,d) for n in cases for v in classes for d in [False,True]]))
for name in cases:
 pair={r['version']:r['cycles'] for r in records if r['case']==name and not r['delay']}
 assert pair['candidate']<=pair['baseline'],pair
 if name=='forward':assert pair['candidate']<pair['baseline'],pair
(work/'machine/results.json').write_text(json.dumps(records,indent=2))
print(f'{len(records)}/{len(records)} machine alias/clobber/delay checks passed')
