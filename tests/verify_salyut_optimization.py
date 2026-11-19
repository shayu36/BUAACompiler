"""Compare new passes with the last judge-accepted compiler and independent outputs."""
from pathlib import Path
import argparse
import json
from verify_optimization import FLAGS, run, simulate, PREFIX, ROOT

WORK = Path('D:/RunIdea/mycomplier-salyut-20261115')
CASES = {
    'recursive-constant': ('''int fib(int n){if(n<2)return n;return fib(n-1)+fib(n-2);}
int main(){printf("%d",fib(18));return 0;}''', b'', b'2584'),
    'recursive-input': ('''int fib(int n){if(n<2)return n;return fib(n-1)+fib(n-2);}
int main(){int n=get_int();printf("%d %d",fib(n),fib(n+1));return 0;}''', b'18\n', b'2584 4181'),
    'invariant-call': ('''int calc(int n){int i=0;int s=0;while(i<400){s=s+n*i;i=i+1;}return s;}
int main(){int n=get_int();int i=0;int sum=0;while(i<50){sum=sum+calc(n);i=i+1;}printf("%d",sum);return 0;}''', b'7\n', b'27930000'),
    'wrapper': ('''int salt;int val(int x){return x+salt;}
int main(){int n=get_int();salt=n+17-n-17;int i=0;int s=0;while(i<1000){s=s+val(3);i=i+1;}printf("%d",s);return 0;}''', b'5\n', b'3000'),
    'mutable-global': ('''int g;int next(){g=g+1;return g;}int read(){return g;}
int main(){int i=0;int s=0;while(i<20){s=s+next()+read();i=i+1;}printf("%d",s);return 0;}''', b'', b'420'),
    'zero-trip': ('''int forever(int n){while(n>0){n=n+0;}return 7;}
int main(){int n=get_int();int i=0;while(i<n){printf("%d",forever(1));i=i+1;}printf("ok");return 0;}''', b'0\n', b'ok'),
    'changing-argument': ('''int f(int n){int x=0;while(n>0){x=x+n;n=n-1;}return x;}
int main(){int i=0;int s=0;while(i<20){s=s+f(i);i=i+1;}printf("%d",s);return 0;}''', b'', b'1330'),
    'nested-cache': ('''int calc(int n){int i=0;int s=0;while(i<20){s=s+n;i=i+1;}return s;}
int main(){int n=get_int();int i=0;int s=0;while(i<3){int j=0;while(j<4){s=s+calc(n+i);j=j+1;}i=i+1;}printf("%d",s);return 0;}''', b'2\n', b'720'),
    'memo-collision': ('''int f(int n,int k){if(n<2)return k;return f(n-1,k)+f(n-2,k);}
int main(){int n=get_int();printf("%d %d %d",f(n,1),f(n,257),f(n,1));return 0;}''', b'8\n', b'34 8738 34'),
    'recursive-side-effect': ('''int g;int f(int n){if(n<2){g=g+1;return n;}return f(n-1)+f(n-2);}
int main(){int n=get_int();int x=f(n);printf("%d %d",x,g);return 0;}''', b'6\n', b'8 13'),
}

def verify(classes, require_gain, baseline, max_regression):
    records=[]
    for name,(source,data,expected) in CASES.items():
        row={'case':name}
        for version,path in [('baseline',baseline),('candidate',classes)]:
            directory=WORK/'cases'/name/version
            directory.mkdir(parents=True,exist_ok=True)
            (directory/'testfile.txt').write_text(source,encoding='utf-8')
            run(['java',*FLAGS,'-cp',str(path),'Compiler'],directory)
            assert not (directory/'error.txt').read_text().strip(), name
            actual,stats=simulate(directory,data)
            assert actual==expected,(name,version,actual,expected)
            row[version]=stats['finalCycles']
        records.append(row)
        print(row,flush=True)
    (WORK/'performance.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
    if require_gain:
        for row in records[:4]:
            assert row['candidate'] < row['baseline']*0.75, row
        recursive = next(row for row in records if row['case'] == 'recursive-input')
        assert recursive['candidate'] < recursive['baseline'] * 0.1, recursive
    if max_regression is not None:
        for row in records:
            assert row['candidate'] <= row['baseline'] * max_regression, row
    print('All Salyut-inspired regression cases passed')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--classes',type=Path,default=WORK/'classes')
    parser.add_argument('--require-gain',action='store_true')
    parser.add_argument('--baseline',type=Path,default=WORK/'baseline')
    parser.add_argument('--max-regression',type=float)
    args=parser.parse_args()
    verify(args.classes,args.require_gain,args.baseline,args.max_regression)
