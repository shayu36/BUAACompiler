"""Exercise width-sensitive arithmetic with live inputs and across runtime calls."""
import argparse
import json
from pathlib import Path
from verify_phase1 import ROOT, FLAGS, MARS, CLANG, run

p=argparse.ArgumentParser()
p.add_argument('--classes',type=Path,default=ROOT/'tests/.phase2-work/full/classes')
args=p.parse_args()
run(['javac','--release','17','-encoding','UTF-8','-cp',args.classes,'-d',args.classes,
     ROOT/'tests/RegisterAllocationIrCases.java',ROOT/'tests/OptimizationIrCases.java'],ROOT)
records=[]
for case,entry,expected,data in [
    ('narrow','OptimizationIrCases','1 255 255 2147483647 1 255 0 1 0 1 254',b''),
    ('escape','OptimizationIrCases','7 9',b''),
    ('narrow_live','RegisterAllocationIrCases','511 255 0 1 0 511 255',b'511\n')]:
    for allocation in ('off','on'):
        folder=args.classes.parent/'typed-ir'/case/allocation
        folder.mkdir(parents=True,exist_ok=True)
        run(['java',*FLAGS,'-Dcompiler.registers='+allocation,'-cp',args.classes,entry,case],folder)
        run([CLANG,'-Wno-override-module','-c','-x','ir','optimized.ll','-o','verified.obj'],folder)
        result=run(['java',*FLAGS,'-jar',MARS,'mips.txt'],folder,data)
        output=result.stdout.decode().replace('\r\n','\n').split('\n',1)[1].strip()
        assert output==expected,(case,allocation,output,expected)
        assert not result.stderr,result.stderr
        records.append({'case':case,'registers':allocation,'passed':True,'llvm_verified':True})
(args.classes.parent/'ir-results.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
print('6/6 typed IR executions passed')
