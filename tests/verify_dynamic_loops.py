"""Runtime trip counts, including unsigned trip counts crossing signed i32 bounds."""
from pathlib import Path
import argparse,json
from verify_optimization import ROOT,FLAGS,run,simulate,simulate_delayed
WORK=Path('D:/RunIdea/mycomplier-dynamic-20261115')
def wrap(n):return (n+2**31)%2**32-2**31

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--build',action='store_true')
    p.add_argument('--classes',type=Path,default=WORK/'classes')
    p.add_argument('--require-gain',action='store_true')
    p.add_argument('--only')
    args=p.parse_args()
    if args.build:
        args.classes.mkdir(parents=True,exist_ok=True)
        run(['javac','--release','17','-encoding','UTF-8','-d',str(args.classes),*map(str,(ROOT/'src').rglob('*.java'))],ROOT)
    sources={
        'sum':'int main(){int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i<end){s=s+i;t=t+3;i=i+1;}printf("%d %d %d",i,s,t);return 0;}',
        'sum-down':'int main(){int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i>end){s=s+i;t=t+3;i=i-1;}printf("%d %d %d",i,s,t);return 0;}',
        'sum-ne':'int main(){int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i!=end){s=s+i;t=t+3;i=i+1;}printf("%d %d %d",i,s,t);return 0;}',
        'sum-ne-down':'int main(){int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i!=end){s=s+i;t=t+3;i=i-1;}printf("%d %d %d",i,s,t);return 0;}',
        'sum-inclusive':'int main(){int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i<=end){s=s+i;t=t+3;i=i+1;}printf("%d %d %d",i,s,t);return 0;}',
        'sum-inclusive-down':'int main(){int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i>=end){s=s+i;t=t+3;i=i-1;}printf("%d %d %d",i,s,t);return 0;}',
        'last':'int main(){int start=get_int();int end=get_int();int i=start;int last=9;while(i<end){last=3*i+2;i=i+1;}printf("%d %d",i,last);return 0;}',
        'invariant':'int main(){int start=get_int();int end=get_int();int n=get_int();int i=start;int s=n;while(i<end){s=s+n;i=i+1;}printf("%d",s);return 0;}',
    }
    cases=[(-3,9),(0,100000),(-1000,1000),(10,10),(15,3),(-2147483648,-2147483644),(2147483643,2147483647),(-2147483648,2147483647),(-2147483648,0),(-1,2147483647)]
    rows=[]
    for name,source in sources.items():
        if args.only and args.only!=name:continue
        dirs={}
        for version,classes in [('baseline',WORK/'baseline'),('candidate',args.classes)]:
            d=WORK/name/version;d.mkdir(parents=True,exist_ok=True);dirs[version]=d
            (d/'testfile.txt').write_text(source,encoding='utf-8')
            run(['java',*FLAGS,'-cp',str(classes),'Compiler'],d)
            assert not (d/'error.txt').read_text().strip()
        for start,end in cases:
            if name in ('sum-down','sum-inclusive-down'): start,end=end,start
            if name=='sum-inclusive': end=min(end,2147483646)
            if name=='sum-inclusive-down': end=max(end,-2147483647)
            n=max(0,start-end) if name=='sum-down' else max(0,end-start)
            if name=='sum-ne': n=(end-start)%(2**32)
            if name=='sum-ne-down': n=(start-end)%(2**32)
            if name=='sum-inclusive': n=max(0,end-start+1)
            if name=='sum-inclusive-down': n=max(0,start-end+1)
            expected={'sum':f'{max(start,end)} {wrap(7+n*(2*start+n-1)//2)} {wrap(9+3*n)}',
                      'sum-down':f'{min(start,end)} {wrap(7+n*(2*start-n+1)//2)} {wrap(9+3*n)}',
                      'sum-ne':f'{end} {wrap(7+n*(2*start+n-1)//2)} {wrap(9+3*n)}',
                      'sum-ne-down':f'{end} {wrap(7+n*(2*start-n+1)//2)} {wrap(9+3*n)}',
                      'sum-inclusive':f'{end+1 if n else start} {wrap(7+n*(2*start+n-1)//2)} {wrap(9+3*n)}',
                      'sum-inclusive-down':f'{end-1 if n else start} {wrap(7+n*(2*start-n+1)//2)} {wrap(9+3*n)}',
                      'last':f'{max(start,end)} {wrap(3*(end-1)+2) if n else 9}',
                      'invariant':str(wrap(13*(n+1)))}[name].encode()
            data=f'{start}\n{end}\n13\n'.encode()
            row=dict(case=name,start=start,end=end)
            for version,d in dirs.items():
                if n>100000 and version=='baseline':continue
                # Large-trip candidate regressions must not simulate billions of instructions.
                if n>100000:
                    ir=(d/'llvm_ir.txt').read_text(encoding='utf-8')
                    assert 'udiv' in ir or len(ir)<1600,(name,'expected summarized loop',len(ir))
                actual,stats=simulate(d,data)
                assert actual==expected,(name,start,end,version,actual,expected)
                row[version]=stats['finalCycles']
                if version=='candidate':
                    assert simulate_delayed(d,args.classes,data)==expected,(name,start,end)
            rows.append(row)
            print(row,flush=True)
            if args.require_gain and n==100000:assert row['candidate']<row['baseline']*0.01,row
    (WORK/'results.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
    print(f'{len(rows)} runtime-boundary cases passed, normal and delayed MARS')
if __name__=='__main__':main()
