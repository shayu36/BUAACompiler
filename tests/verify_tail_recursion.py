"""Tail-call parameter permutations, effects, and local-array lifetime guard."""
from pathlib import Path
import argparse,json
from verify_optimization import FLAGS,run,simulate,simulate_delayed
WORK=Path('D:/RunIdea/mycomplier-tail-20261115')
CASES={
    'sum':('int f(int n,int s){if(n<=0)return s;return f(n-1,s+n);}int main(){int n=get_int();printf("%d",f(n,7));return 0;}',b'2000\n',b'2001007'),
    'rotate':('int f(int n,int a,int b,int c){if(n==0)return a*100+b*10+c;return f(n-1,b,c,a);}int main(){int n=get_int();printf("%d",f(n,1,2,3));return 0;}',b'2000\n',b'312'),
    'effect':('int g;int f(int n,int s){g=g+1;if(n<=0)return s;return f(n-1,s+n);}int main(){int n=get_int();int x=f(n,0);printf("%d %d",x,g);return 0;}',b'2000\n',b'2001000 2001'),
    'array-param':('int f(int a[],int n){if(n==0)return a[0];a[n-1]=a[n-1]+n;return f(a,n-1);}int main(){int a[4]={1,2,3,4};int n=get_int();int x=f(a,n);printf("%d %d %d %d",x,a[1],a[2],a[3]);return 0;}',b'4\n',b'2 4 6 8'),
    'array-lifetime':('int f(int p[],int n){int a[1]={n+7};if(n==0)return p[0];return f(a,n-1);}int main(){int a[1]={0};int n=get_int();printf("%d",f(a,n));return 0;}',b'8\n',b'8'),
    'multiple-tails':('int f(int n,int s){if(n<=0)return s;if(n%2==0)return f(n-1,s+2);return f(n-1,s+3);}int main(){int n=get_int();printf("%d",f(n,7));return 0;}',b'20\n',b'57'),
    'six-params':('int f(int n,int a,int b,int c,int d,int e){if(n==0)return a+10*b+100*c+1000*d+10000*e;return f(n-1,b,c,d,e,a);}int main(){int n=get_int();printf("%d",f(n,1,2,3,4,5));return 0;}',b'8\n',b'32154'),
    'observer-call':('int g;int seen;void observe(){seen=seen+g;}int f(int n){g=g+1;observe();if(n==0)return g;return f(n-1);}int main(){int n=get_int();int x=f(n);printf("%d %d %d",x,g,seen);return 0;}',b'8\n',b'9 9 45'),
    'non-tail':('int f(int n){if(n<=1)return 1;int x=f(n-1);return n*x;}int main(){int n=get_int();printf("%d",f(n));return 0;}',b'8\n',b'40320'),
    'char-return':("char f(int n,char c){if(n==0)return c;return f(n-1,(char)((int)c+1));}int main(){int n=get_int();printf(\"%c\",f(n,'A'));return 0;}",b'8\n',b'I'),
}
parser=argparse.ArgumentParser()
parser.add_argument('--classes',type=Path,default=WORK/'classes')
parser.add_argument('--require-gain',action='store_true')
args=parser.parse_args()
rows=[]
for name,(source,data,expected) in CASES.items():
    row={'case':name}
    for version,classes in [('baseline',Path('D:/RunIdea/mycomplier-branch-20261115/classes')),('candidate',args.classes)]:
        d=WORK/name/version;d.mkdir(parents=True,exist_ok=True)
        (d/'testfile.txt').write_text(source,encoding='utf-8')
        run(['java',*FLAGS,'-cp',str(classes),'Compiler'],d)
        assert not (d/'error.txt').read_text().strip(),name
        actual,stats=simulate(d,data)
        assert actual==expected,(name,version,actual,expected)
        row[version]=stats['finalCycles']
        if version=='candidate':
            assert simulate_delayed(d,classes,data)==expected,name
    rows.append(row);print(row,flush=True)
(WORK/'performance.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
if args.require_gain:
    for row in rows[:3]:assert row['candidate']<row['baseline']*0.5,row
