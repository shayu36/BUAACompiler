"""Independent signed/unsigned predicate oracle, both delay modes and long edges."""
import argparse
import concurrent.futures
import json
from pathlib import Path
from verify_phase1 import ROOT, FLAGS, MARS, CLANG, run
from branch_fusion_cases import predicate

p=argparse.ArgumentParser()
p.add_argument('--work',type=Path,default=ROOT/'tests/.branch-dfdace68')
args=p.parse_args()
work=args.work
ops=['<','<=','>','>=','ult','ule','ugt','uge','==','!=']
unsigned={'ult':'<','ule':'<=','ugt':'>','uge':'>='}
values={32:[(-2147483648,2147483647),(2147483647,-2147483648),(-1,0),(0,-1),(7,7)],
        8:[(128,127),(127,128),(255,0),(0,255),(255,255)],
        1:[(1,0),(0,1),(1,1),(0,0)]}
jobs=[]
for version in ['baseline','candidate']:
    classes=work/'final'/(version+'-classes')
    run(['javac','--release','17','-encoding','UTF-8','-cp',classes,'-d',classes,ROOT/'tests/BranchIrCases.java'],ROOT)
    for width in [32,8,1]:
        for mode in ['truefirst','falsefirst','shared','nonadjacent','far']:
            if mode in ['nonadjacent','far'] and width!=32: continue
            for delay in [False,True]:
                folder=work/'branch-ir'/version/f'{width}_{mode}_{delay}'
                folder.mkdir(parents=True,exist_ok=True)
                run(['java',*FLAGS,*(['-Dcompiler.mips.delaySlots=true'] if delay else []),'-cp',classes,'BranchIrCases',str(width),mode],folder)
                run([CLANG,'-Wno-override-module','-c','-x','ir','optimized.ll','-o','verified.obj'],folder)
                if mode=='far':
                    assert (folder/'mips.txt').read_text().count('syscall')>12000
                jobs.append((folder,version,width,mode,delay,values[width][:2] if mode=='far' else values[width]))

def check(job):
    folder,version,width,mode,delay,pairs=job
    records=[]
    for a,b in pairs:
        mask=(1<<width)-1
        ua,ub=a&mask,b&mask
        sa=ua-(1<<width) if ua&(1<<(width-1)) else ua
        sb=ub-(1<<width) if ub&(1<<(width-1)) else ub
        expected=[]
        for op in ['>='] if mode=='far' else ops:
            truth=predicate(ua,unsigned[op],ub) if op in unsigned else predicate(sa,op,sb)
            token=str(int(truth))
            if mode=='shared': token+=str(int(truth))
            if mode=='nonadjacent': token+=str((a+b+2**31)%2**32-2**31)
            expected.append(token)
        command=['java',*FLAGS,'-jar']
        command+=([Path('D:/Accepted/tools/MARS-2024.jar'),'nc','me','ae91','se92','db','sm','mips.txt'] if delay else [MARS,'mips.txt'])
        r=run(command,folder,f'{a}\n{b}\n'.encode())
        actual=r.stdout.decode() if delay else r.stdout.decode().split('\n',1)[1]
        assert actual.strip()==' '.join(expected),(version,width,mode,delay,a,b,actual,expected)
        assert not r.stderr.strip(),r.stderr
        records.append(dict(version=version,width=width,mode=mode,delay=delay,a=a,b=b,passed=True))
    return records

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    records=[r for batch in pool.map(check,jobs) for r in batch]
(work/'branch-ir/results.json').write_text(json.dumps(records,indent=2))
print(f'{len(records)}/{len(records)} direct IR predicate executions passed',flush=True)
