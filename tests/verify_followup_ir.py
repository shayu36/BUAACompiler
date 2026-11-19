"""Check signed/unsigned keys and division boundaries using an independent oracle."""
import json
from verify_phase1 import ROOT, FLAGS, MARS, CLANG, run
from divrem_cases import quotient, remainder

work=ROOT/'tests/.followup-b4616102'
wrap=lambda n:(n+2**31)%2**32-2**31
records=[]
for version in ['baseline','candidate']:
    classes=work/'final'/(version+'-classes')
    run(['javac','--release','17','-encoding','UTF-8','-cp',classes,'-d',classes,ROOT/'tests/DivRemIrCases.java'],ROOT)
    for mode in ['signed','unsigned']:
        for index,(a,b) in enumerate([(-7,3),(7,-3),(-2**31,-1),(-2**31,7),(2**31-1,-2**31)]):
            folder=work/'typed-ir'/version/mode/str(index)
            folder.mkdir(parents=True,exist_ok=True)
            run(['java',*FLAGS,'-cp',classes,'DivRemIrCases',mode],folder)
            run([CLANG,'-Wno-override-module','-c','-x','ir','optimized.ll','-o','verified.obj'],folder)
            sq=wrap(quotient(a,b)); sr=remainder(a,b)
            uq=wrap((a&0xffffffff)//(b&0xffffffff)); ur=wrap((a&0xffffffff)%(b&0xffffffff))
            expected=f'{sq} {ur} {sr}' if mode=='signed' else f'{uq} {sr} {ur}'
            r=run(['java',*FLAGS,'-jar',MARS,'mips.txt'],folder,f'{a}\n{b}\n'.encode())
            actual=r.stdout.decode().split('\n',1)[1].strip()
            assert actual==expected,(version,mode,a,b,actual,expected)
            assert not r.stderr,r.stderr
            records.append(dict(version=version,mode=mode,dividend=a,divisor=b,passed=True))
(work/'typed-ir/results.json').write_text(json.dumps(records,indent=2))
print(f'{len(records)}/{len(records)} signed/unsigned IR executions passed')
