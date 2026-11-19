"""Public testcase14, common inputs and an independent convolution oracle."""
import json
import random
import re
import argparse
from verify_optimization import ROOT,FLAGS,run,simulate
from verify_loop_optimization import WORK

source=(ROOT/'tests/testcase14/testfile.txt').read_text(encoding='utf-8')
parser=argparse.ArgumentParser()
parser.add_argument('--classes',type=type(WORK),default=WORK/'classes')
parser.add_argument('--baseline',type=type(WORK),default=WORK/'baseline')
args=parser.parse_args()
rng=random.Random(20261115)
cases=[([2],[3]),([1,2,3],[4,5]),([0,0],[0,0]),([rng.randrange(10000) for _ in range(17)],[rng.randrange(10000) for _ in range(23)])]
records=[]
for version,classes in [('salyut',WORK/'salyut-classes'),('baseline',args.baseline),('candidate',args.classes)]:
    directory=WORK/'public-comparison'/version
    directory.mkdir(parents=True,exist_ok=True)
    adapted=source
    if version=='salyut':
        adapted=source.replace('get_int()', 'getint()')
        adapted=re.sub(r'while\s*\((.*?)\)\s*\{',r'for(;\1;){',adapted)
    (directory/'testfile.txt').write_text(adapted,encoding='utf-8')
    try:
        run(['java',*FLAGS,'-cp',str(classes),'Compiler'],directory,timeout=60)
        if (directory/'error.txt').exists(): assert not (directory/'error.txt').read_text().strip()
        for number,(a,b) in enumerate(cases):
            oracle=[0]*(len(a)+len(b)-1)
            for i,x in enumerate(a):
                for j,y in enumerate(b):oracle[i+j]=(oracle[i+j]+x*y)%998244353
            expected=(str(len(oracle))+':'+''.join(' '+str(n) for n in oracle)).encode()
            data=('\n'.join(map(str,[len(a),*a,len(b),*b]))+'\n').encode()
            actual,stats=simulate(directory,data)
            assert actual==expected,(number,actual,expected)
            row=dict(version=version,input=number,correct=True,finalCycles=stats['finalCycles'])
            records.append(row)
            print(row,flush=True)
    except Exception as error:
        row=dict(version=version,correct=False,error=str(error)[:1200])
        records.append(row)
        print(row,flush=True)
        if version!='salyut':raise
(WORK/'public-comparison.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
