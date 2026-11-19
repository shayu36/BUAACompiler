"""Compare the accepted IR optimizer with and without register allocation."""
import argparse
import concurrent.futures
import json
from pathlib import Path
import re
import verify_phase1 as base

WORK = base.ROOT/'tests/.phase2-work'
CASES = {
    'unused_parameters': ('int f(int a,int b,int c,int d,int e,int g){return 42;}int main(){printf("%d",f(1,2,3,4,5,6));return 0;}','','42'),
    'call_loop': ('''int f(int x){return x*3+1;}int main(){int n=get_int();int i=0;int s=0;
while(i<n){s=s+f(i)+i;i=i+1;}printf("%d %d",s,i);return 0;}''','8\n','120 8'),
    'six_argument_recursion': ('''int f(int a,int b,int c,int d,int e,int n){
if(n==0)return a+b+c+d+e;int x=f(a+1,b+2,c+3,d+4,e+5,n-1);return x+a+b+c+d+e;}
int main(){printf("%d",f(1,2,3,4,5,3));return 0;}''','','150'),
    'large_frame': ('''int main(){int a[20000];int n=get_int();a[0]=n;a[19999]=n+7;
printf("%d %d",a[0],a[19999]);return 0;}''','18\n','18 25'),
    'array_call': ('''int f(int a[],int b[],int n){int i=0;int s=0;while(i<n){a[i]=b[i]+i;s=s+a[i];i=i+1;}return s;}
int main(){int a[5]={3,5,7,9,11};printf("%d %d",f(a,a,5),a[4]);return 0;}''','','45 15'),
    'nested_calls': ('''int f(int n){if(n<2)return n+1;int a=f(n-1);int b=f(n-2);return a+b+n;}
int main(){int n=get_int();int a=f(n);int b=f(n-1);printf("%d %d %d",a,b,n);return 0;}''','7\n','100 59 7'),
}

def pressure_case():
    declarations=''.join(f'int x{i}=get_int();' for i in range(24))
    # All 24 inputs are live across I/O calls; eight saved registers cannot hold them.
    expression='+'.join(f'x{i}*{i+2}' for i in range(24))
    expected=sum((i+1)*(i+2) for i in range(24))
    return ('int main(){'+declarations+'printf("%d",'+expression+');return 0;}',
            ''.join(f'{i+1}\n' for i in range(24)),str(expected))

if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--label',default='current')
    p.add_argument('--source',type=Path,default=base.ROOT/'src')
    p.add_argument('--public',action='store_true')
    p.add_argument('--fuzz',action='store_true')
    p.add_argument('--llvm',action='store_true')
    p.add_argument('--expect-improvement',action='store_true')
    p.add_argument('--only',default='')
    args=p.parse_args()
    base.WORK=WORK
    classes=WORK/args.label/'classes'
    base.build(args.source,classes)
    cases={**base.CASES,**CASES,'pressure':pressure_case()}
    if args.public:cases.update(base.public_cases())
    if args.fuzz:cases.update(base.fuzz_cases())
    if args.only:cases={k:v for k,v in cases.items() if re.search(args.only,k)}
    jobs=[(name,fixture,allocation) for name,fixture in cases.items() for allocation in ('off','on')]
    def verify(job):
        name,fixture,allocation=job
        r=base.verify_one(classes,name,fixture,'on',args.label+'/'+allocation,True,args.llvm,
                          ['-Dcompiler.registers='+allocation])
        r['registers']=allocation
        return r
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        records=list(pool.map(verify,jobs))
    failures=[r for r in records if not r['passed']]
    for r in failures:print(json.dumps(r,ensure_ascii=True),flush=True)
    (WORK/args.label/'results.json').write_text(json.dumps(records,indent=2,ensure_ascii=False),encoding='utf-8')
    print(f'{len(records)-len(failures)}/{len(records)} passed',flush=True)
    if args.expect_improvement and not failures:
        unused={r['registers']:r for r in records if r['case']=='unused_parameters'}
        if unused:
            assert unused['on']['cycles']<=unused['off']['cycles'], 'unused parameters caused unnecessary register loads'
        for case in ('loop','call_loop','pressure'):
            pair={r['registers']:r for r in records if r['case']==case}
            if pair:
                assert pair['on']['memory']<pair['off']['memory'],(case,'register allocation did not reduce memory traffic')
                assert pair['on']['cycles']<pair['off']['cycles'],(case,'register allocation did not improve cycles')
    raise SystemExit(bool(failures))
