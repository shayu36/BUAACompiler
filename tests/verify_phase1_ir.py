"""Exercise narrow integers, unsigned arithmetic and escaped scalar addresses."""
import argparse
import json
from verify_phase1 import ROOT, WORK, FLAGS, MARS, CLANG, run

p = argparse.ArgumentParser()
p.add_argument('--label', default='final')
args = p.parse_args()
classes = WORK/args.label/'classes'
run(['javac','--release','17','-encoding','UTF-8','-cp',classes,'-d',classes,
     ROOT/'tests/OptimizationIrCases.java'],ROOT)
records = []
for case, expected in [('narrow','1 255 255 2147483647 1 255 0 1 0 1 254'),('escape','7 9')]:
    for mode in ('off','on'):
        folder = WORK/args.label/('ir_'+case)/mode
        folder.mkdir(parents=True,exist_ok=True)
        run(['java',*FLAGS,'-Dcompiler.optimize='+mode,'-cp',classes,'OptimizationIrCases',case],folder)
        run([CLANG,'-Wno-override-module','-c','-x','ir','optimized.ll','-o','verified.obj'],folder)
        result = run(['java',*FLAGS,'-jar',MARS,'mips.txt'],folder)
        output = result.stdout.decode().replace('\r\n','\n').split('\n',1)[1].strip()
        assert output == expected,(case,mode,output,expected)
        assert not result.stderr,result.stderr
        records.append({'case':case,'mode':mode,'passed':True,'llvm_verified':True,
                        'cycles':json.loads((folder/'instruction_statistics.json').read_text())['finalCycles']})
(WORK/args.label/'ir-results.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
print('4/4 typed IR/MARS executions passed')
