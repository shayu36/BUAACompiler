"""Byte-for-byte fallback and determinism checks against the accepted phase-one runs."""
import argparse
import concurrent.futures
from pathlib import Path
from verify_phase1 import ROOT, FLAGS, run

p=argparse.ArgumentParser()
p.add_argument('--classes',type=Path,default=ROOT/'tests/.phase2-work/final/classes')
args=p.parse_args()
baseline=ROOT/'tests/.phase1-work/final'
work=args.classes.parent/'compatibility'
jobs=list(baseline.glob('*/off/testfile.txt'))
assert len(jobs)==106,('Missing phase-one baseline',len(jobs))

def verify(source):
    name=source.parent.parent.name
    for mode in ('off','on'):
        folder=work/name/mode
        folder.mkdir(parents=True,exist_ok=True)
        (folder/'testfile.txt').write_bytes(source.read_bytes())
        command=['java',*FLAGS,'-Dcompiler.optimize='+mode,'-Dcompiler.registers=off',
                 '-cp',args.classes,'OptimizationProbe']
        run(command,folder)
        assert (folder/'mips.txt').read_bytes()==(baseline/name/mode/'mips.txt').read_bytes(),(name,mode,'fallback changed')
    # Recompilation in distinct JVMs must not depend on hash/identity iteration order.
    command=['java',*FLAGS,'-cp',args.classes,'OptimizationProbe']
    run(command,folder)
    first=(folder/'mips.txt').read_bytes()
    run(command,folder)
    assert first==(folder/'mips.txt').read_bytes(),(name,'nondeterministic allocation')
    return name

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    checked=list(pool.map(verify,jobs))
print(f'{2*len(checked)} fallback byte comparisons and {len(checked)} determinism comparisons passed')
