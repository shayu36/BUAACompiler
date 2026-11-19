"""Independent signed-32-bit oracle for sequential affine updates and boundary loops."""
import random
import argparse
from pathlib import Path
from verify_optimization import FLAGS,run,simulate,simulate_delayed
from verify_loop_optimization import WORK

parser = argparse.ArgumentParser()
parser.add_argument('--classes', type=Path, default=WORK/'classes')
args = parser.parse_args()

def wrap(n): return (n+2**31)%2**32-2**31
rng=random.Random(24373313)
functions=[]
expected=[]
for case in range(36):
    count=rng.choice([0,1,2,7,31,100,4096])
    step=rng.choice([-3,-1,1,4])
    start=rng.randrange(-100,101)
    end=start+count*step
    op='<' if step>0 else '>'
    values=[rng.randrange(-100,101) for _ in range(3)]
    initial=values.copy()
    updates=[]
    for target in range(3):
        a,b,c,d=[rng.choice([-65536,-3,-1,0,1,2,65536]) for _ in range(4)]
        source=rng.randrange(3)
        updates.append((target,source,a,b,c,d))
    for j in range(count):
        for target,source,a,b,c,d in updates:
            values[target]=wrap(a*values[target]+b*values[source]+c*(start+j*step)+d*13)
    body=''.join(f'x{t}={a}*x{t}+{b}*x{s}+{c}*i+{d}*n;' for t,s,a,b,c,d in updates)
    init=''.join(f'int x{i}={v};' for i,v in enumerate(initial))
    functions.append(f'int f{case}(int n){{{init}int i={start};while(i{op}{end}){{{body}i=i+({step});}}printf("%d %d %d %d\\n",x0,x1,x2,i);return 0;}}')
    expected.append(' '.join(map(str,[*values,end])))
source='\n'.join(functions)+'\nint main(){int n=get_int();'+''.join(f'f{i}(n);' for i in range(36))+'return 0;}'
directory=WORK/'affine-fuzz'
directory.mkdir(parents=True,exist_ok=True)
(directory/'testfile.txt').write_text(source,encoding='utf-8')
run(['java',*FLAGS,'-cp',str(args.classes),'Compiler'],directory)
assert not (directory/'error.txt').read_text().strip()
actual,stats=simulate(directory,b'13\n')
assert actual.decode().splitlines()==expected,(actual,expected)
assert simulate_delayed(directory,args.classes,b'13\n').decode().splitlines()==expected
print('36 affine state/overflow differential cases passed, normal and delayed MARS;',stats['finalCycles'])
