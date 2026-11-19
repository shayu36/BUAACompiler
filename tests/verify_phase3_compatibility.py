"""Fallback bytes, deterministic builds, delay slots and nonterminating boundaries."""
import argparse
import concurrent.futures
import json
from pathlib import Path
import verify_phase1 as base
from phase3_cases import CASES

p=argparse.ArgumentParser()
p.add_argument('--classes',type=Path,default=base.ROOT/'tests/.phase3-work/final/classes')
args=p.parse_args()
work=base.ROOT/'tests/.phase3-work/compatibility'
vanilla=Path('D:/Accepted/tools/MARS-2024.jar')

def compile_at(folder,source,properties=()):
    folder.mkdir(parents=True,exist_ok=True)
    (folder/'testfile.txt').write_text(source,encoding='utf-8')
    base.run(['java',*base.FLAGS,*properties,'-cp',args.classes,'OptimizationProbe'],folder)
    assert not (folder/'error.txt').read_text().strip()
    return (folder/'mips.txt').read_bytes()

def fallback(source):
    name=source.parent.parent.name
    folder=work/'fallback'/name
    actual=compile_at(folder,source.read_text(encoding='utf-8'),['-Dcompiler.optimize=off'])
    assert actual==(source.parent/'mips.txt').read_bytes(),(name,'off fallback')
    actual=compile_at(folder,source.read_text(encoding='utf-8'))
    again=compile_at(folder,source.read_text(encoding='utf-8'))
    assert actual==again,(name,'determinism')
    return name

sources=list((base.ROOT/'tests/.phase1-work/final').glob('*/off/testfile.txt'))
assert len(sources)==106,len(sources)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    checked=list(pool.map(fallback,sources))
print(f'{len(checked)} fallback byte comparisons and {len(checked)} determinism checks passed',flush=True)

selected={**base.CASES,**{k:v for k,v in CASES.items() if k in ['arithmetic_boundaries','memo_collision','impure_recursion'] or k.startswith('boundary_')}}
for name,(source,data,expected) in selected.items():
    folder=work/'delayed'/name
    assembly=compile_at(folder,source,['-Dcompiler.mips.delaySlots=true'])
    assert b'    nop' in assembly
    r=base.run(['java',*base.FLAGS,'-jar',vanilla,'nc','me','ae91','se92','db','sm','mips.txt'],folder,data.encode(),timeout=20)
    assert r.stdout.decode().strip()==expected.strip(),(name,r.stdout,expected)
    assert not r.stderr.strip(),(name,r.stderr)
print(f'{len(selected)} delayed MARS executions passed',flush=True)

for step,op,end in [(1,'<=',2**31-1),(-1,'>=',-2**31)]:
    source=f'int main(){{int end=get_int();int i=end;while(i{op}end){{i=i+({step});}}printf("WRONG");return 0;}}'
    for delay in [False,True]:
        folder=work/f'infinite_{step}_{delay}'
        compile_at(folder,source,['-Dcompiler.mips.delaySlots=true'] if delay else [])
        r=base.run(['java',*base.FLAGS,'-jar',vanilla,'nc','me','ae91','se92','sm','10000',*(['db'] if delay else []),'mips.txt'],folder,f'{end}\n'.encode())
        assert not r.stdout.strip() and b'maximum' in r.stderr.lower(),(step,delay,r.stdout,r.stderr)
print('4/4 extreme inclusive-bound loops retain nontermination',flush=True)
(work/'results.json').write_text(json.dumps(dict(fallback=len(checked),determinism=len(checked),delayed=len(selected),nontermination=4,passed=True),indent=2))
