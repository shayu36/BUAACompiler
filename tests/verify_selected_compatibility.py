"""Check the selected compiler's existing off switch and delayed MARS target."""
import concurrent.futures
import argparse
import json
from pathlib import Path
import verify_phase1 as base
from phase3_cases import CASES as ADVANCED
from memory_forward_cases import CASES as MEMORY
from verify_selected_baseline import WORK

p=argparse.ArgumentParser()
p.add_argument('--work',type=Path,default=WORK)
p.add_argument('--divrem',action='store_true')
p.add_argument('--branches',action='store_true')
p.add_argument('--leaf',action='store_true')
p.add_argument('--peephole',action='store_true')
p.add_argument('--small-multiply',action='store_true')
p.add_argument('--leaf-arguments',action='store_true')
args=p.parse_args()
WORK=args.work

classes=WORK/'final/candidate-classes'
baseline=WORK/'final/baseline-classes'
vanilla=Path('D:/Accepted/tools/MARS-2024.jar')

def compile_source(folder,source,cl,properties):
    folder.mkdir(parents=True,exist_ok=True)
    (folder/'testfile.txt').write_text(source,encoding='utf-8')
    base.run(['java',*base.FLAGS,*properties,'-cp',cl,'Compiler'],folder)
    assert not (folder/'error.txt').read_text(encoding='utf-8').strip()
    return (folder/'mips.txt').read_bytes()

def off_case(item):
    name,(source,data,expected)=item
    assembly=[]
    for version,cl in [('baseline',baseline),('candidate',classes)]:
        folder=WORK/'compatibility/off'/version/name
        assembly.append(compile_source(folder,source,cl,['-Dcompiler.optimize=off']))
        r=base.run(['java',*base.FLAGS,'-jar',base.MARS,'mips.txt'],folder,data.encode())
        assert r.stdout.decode().split('\n',1)[1].strip()==expected.strip(),name
        assert not r.stderr,(name,r.stderr)
    assert assembly[0]==assembly[1],name
    return name

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    off=list(pool.map(off_case,base.CASES.items()))
print(f'{len(off)*2} optimization-off executions and {len(off)} baseline byte comparisons passed',flush=True)

selected={**base.CASES,**MEMORY,**{k:v for k,v in ADVANCED.items() if k.startswith('boundary_') or k in ['arithmetic_boundaries','memo_collision','impure_recursion']}}
if args.divrem:
    from divrem_cases import CASES as DIVREM, COPY_CASES
    selected.update(DIVREM)
    selected.update(COPY_CASES)
if args.branches:
    from branch_fusion_cases import CASES as BRANCHES
    selected.update(BRANCHES)
if args.leaf:
    from leaf_return_cases import CASES as LEAF
    selected.update(LEAF)
if args.peephole:
    from peephole_cases import CASES as PEEPHOLE
    selected.update(PEEPHOLE)
if args.small_multiply:
    from small_multiply_cases import CASES as SMALL_MULTIPLY
    selected.update(SMALL_MULTIPLY)
if args.leaf_arguments:
    from leaf_argument_cases import CASES as LEAF_ARGUMENTS
    selected.update(LEAF_ARGUMENTS)
def delayed(item):
    name,(source,data,expected)=item
    folder=WORK/'compatibility/delayed'/name
    assembly=compile_source(folder,source,classes,['-Dcompiler.mips.delaySlots=true'])
    assert b'    nop' in assembly,name
    r=base.run(['java',*base.FLAGS,'-jar',vanilla,'nc','me','ae91','se92','db','sm','mips.txt'],folder,data.encode(),timeout=30)
    assert r.stdout.decode().strip()==expected.strip(),(name,r.stdout,expected)
    assert not r.stderr.strip(),(name,r.stderr)
    return name
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    delays=list(pool.map(delayed,selected.items()))
print(f'{len(delays)} delayed MARS executions passed',flush=True)

for step,op,end in [(1,'<=',2**31-1),(-1,'>=',-2**31)]:
    source=f'int main(){{int end=get_int();int i=end;while(i{op}end){{i=i+({step});}}printf("WRONG");return 0;}}'
    for delay in [False,True]:
        folder=WORK/f'compatibility/infinite_{step}_{delay}'
        compile_source(folder,source,classes,['-Dcompiler.mips.delaySlots=true'] if delay else [])
        r=base.run(['java',*base.FLAGS,'-jar',vanilla,'nc','me','ae91','se92','sm','10000',*(['db'] if delay else []),'mips.txt'],folder,f'{end}\n'.encode())
        assert not r.stdout.strip() and b'maximum' in r.stderr.lower(),(step,delay,r.stdout,r.stderr)
print('4 extreme inclusive-bound loops retain nontermination',flush=True)
(WORK/'compatibility/results.json').write_text(json.dumps(dict(passed=True,off_executions=len(off)*2,off_byte_comparisons=len(off),delayed_executions=len(delays),nontermination=4),indent=2))
