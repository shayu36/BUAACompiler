"""Comparison boundaries and dynamic branch cost, independent signed-integer oracle."""
from pathlib import Path
import argparse,json
from verify_optimization import FLAGS,run,simulate,simulate_delayed
WORK=Path('D:/RunIdea/mycomplier-branch-20261115')
parser=argparse.ArgumentParser()
parser.add_argument('--classes',type=Path,default=WORK/'classes')
parser.add_argument('--require-gain',action='store_true')
args=parser.parse_args()
values=[-2147483648,-32769,-32768,-1,0,1,32766,32767,32768,2147483647]
comparisons=['<','<=','>','>=','==','!=']
parts=[]
for c in values:
    literal='(-2147483647-1)' if c==-2147483648 else str(c)
    for op in comparisons:
        parts.append(f'if(x{op}{literal})printf("1");else printf("0");')
        parts.append(f'if({literal}{op}x)printf("1");else printf("0");')
source='int main(){int x=get_int();'+''.join(parts)+'return 0;}'
rows=[]
for version,classes in [('baseline',Path('D:/RunIdea/mycomplier-dynamic-20261115/classes')),('candidate',args.classes)]:
    d=WORK/version;d.mkdir(parents=True,exist_ok=True)
    (d/'testfile.txt').write_text(source,encoding='utf-8')
    run(['java',*FLAGS,'-cp',str(classes),'Compiler'],d)
    assert not (d/'error.txt').read_text().strip()
    for x in values:
        expected=''.join(str(int(eval(f'{a} {op} {b}'))) for c in values for op in comparisons for a,b in [(x,c),(c,x)]).encode()
        data=f'{x}\n'.encode()
        actual,stats=simulate(d,data)
        assert actual==expected,(version,x,actual,expected)
        if version=='candidate':
            assert simulate_delayed(d,classes,data)==expected,x
        rows.append(dict(version=version,input=x,cycles=stats['finalCycles']))
    print(version,'comparison boundaries passed',flush=True)
before=sum(r['cycles'] for r in rows if r['version']=='baseline')
after=sum(r['cycles'] for r in rows if r['version']=='candidate')
print('Aggregate cycles',before,after,flush=True)
(WORK/'comparison.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
# This boundary test prints every comparison bit, so its I/O cost is unchanged.
if args.require_gain:assert after < before,(before,after)
