"""Loop/control-flow regression and cycle comparison against the last accepted compiler."""
from pathlib import Path
import argparse
import json
from verify_optimization import FLAGS, ROOT, run, simulate, simulate_delayed

WORK = Path('D:/RunIdea/mycomplier-loop-20261115')
CASES = {
    'changing-bound': ('int main(){int n=get_int();int i=0;int s=0;while(i<n){s=s+i;n=n-1;i=i+1;}printf("%d %d %d",i,n,s);return 0;}', b'20\n', b'10 10 45'),
    'dependent-dynamic': ('int main(){int n=get_int();int i=0;int s=3;int t=0;while(i<n){s=s+i;t=t+s;i=i+1;}printf("%d %d",s,t);return 0;}', b'20\n', b'193 1390'),
    'inline-alias': ('int f(int a[],int b[],int n){if(n>0){a[0]=a[0]+n;return b[0];}b[0]=7;return a[0];}int main(){int a[2]={1,2};int n=get_int();int x=f(a,a,n);int y=f(a,a,0);printf("%d %d %d",x,y,a[0]);return 0;}', b'3\n', b'4 7 7'),
    'inline-void': ('void f(int a[],int n){if(n<0){a[0]=1;return;}a[0]=n;return;}int main(){int a[1];int n=get_int();f(a,-2);int x=a[0];f(a,n);printf("%d %d",x,a[0]);return 0;}', b'3\n', b'1 3'),
    'inline-char': ('char f(char x,int n){if(n>0)return (char)((int)x+n);return x;}int main(){int n=get_int();printf("%d",(int)f((char)250,n));return 0;}', b'10\n', b'4'),
    'count-max': ('int main(){int n=get_int();int i=2147483645;while(i<n){i=i+1;}printf("%d",i);return 0;}', b'2147483647\n', b'2147483647'),
    'count-min': ('int main(){int n=get_int();int i=-2147483647-1;while(i<n){i=i+1;}printf("%d",i);return 0;}', b'-2147483646\n', b'-2147483646'),
    'branch-inline': ('int distance(int a,int b){if(a<b)return b-a;if(a==b)return 0;return a-b;}int main(){int n=get_int();int i=0;int s=0;while(i<1000){s=s+distance(n,i);i=i+1;}printf("%d",s);return 0;}', b'17\n', b'482806'),
    'inline-effects': ('int g;int f(int n){g=g+1;if(n<0)return g-n;return g+n;}int main(){int n=get_int();int i=0;int s=0;while(i<10){s=s+f(n-i);i=i+1;}printf("%d %d",s,g);return 0;}', b'3\n', b'82 10'),
    'main-only-global': ('int i;int s=7;int main(){int n=get_int();i=0;while(i<10000){s=s+i+n;i=i+1;}printf("%d %d",s,i);return 0;}', b'3\n', b'50025007 10000'),
    'shared-global': ('int g=2;int f(int n){if(n==0)return g;g=g+1;int t=f(n-1);g=g+2;return t+g;}int main(){int n=get_int();int v=f(n);printf("%d %d",v,g);return 0;}', b'3\n', b'32 11'),
    'count-only': ('int main(){int n=get_int();int i=0;while(i<n){i=i+1;}printf("%d",i);return 0;}', b'100000\n', b'100000'),
    'constant-sum': ('int main(){int s=get_int();int i=0;while(i<100000){s=s+i;i=i+1;}printf("%d %d",s,i);return 0;}', b'7\n', b'704982711 100000'),
    'nested-sum': ('int main(){int n=get_int();int i=0;int s=0;while(i<n){int j=0;while(j<1000){s=s+j+i;j=j+1;}i=i+1;}printf("%d",s);return 0;}', b'100\n', b'54900000'),
    'array-loop': ('int a[1000];int main(){int n=get_int();int i=0;while(i<n){a[i]=i*3;i=i+1;}i=0;int s=0;while(i<n){s=s+a[i];i=i+1;}printf("%d",s);return 0;}', b'1000\n', b'1498500'),
    'zero-negative': ('int main(){int n=get_int();int i=0;int s=7;while(i<n){s=s+i;i=i+1;}printf("%d %d",s,i);return 0;}', b'-2147483648\n', b'7 0'),
    'step': ('int main(){int s=get_int();int i=-4;while(i<100){s=s+i;i=i+3;}printf("%d %d",s,i);return 0;}', b'2\n', b'1647 101'),
    'dependent': ('int main(){int s=get_int();int i=0;int t=0;while(i<20){s=s+i;t=t+s;i=i+1;}printf("%d %d",s,t);return 0;}', b'3\n', b'193 1390'),
    'last-load': ('int main(){int i=0;int x=get_int();int last=9;while(i<10){last=i;x=x+last;i=i+1;}printf("%d %d %d",x,i,last);return 0;}', b'1\n', b'46 10 9'),
    'early-exit': ('int main(){int n=get_int();int i=0;int s=0;while(i<100){i=i+1;if(i==n)break;if(i%3==0)continue;s=s+i;}printf("%d %d",i,s);return 0;}', b'10\n', b'10 27'),
    'char-loop': ('int main(){char c=(char)250;int i=0;while(i<10){c=(char)((int)c+1);i=i+1;}printf("%d",(int)c);return 0;}', b'', b'4'),
    'condition-side-effect': ('int g;int cond(){g=g+1;if(g<6)return 1;return 0;}int main(){int s=0;while(cond()){s=s+g;}printf("%d %d",s,g);return 0;}', b'', b'15 6'),
    'comparison': ('int main(){int a=get_int();int b=get_int();if(a<b)printf("L");if(a>=b)printf("G");if(a==b)printf("E");if(a!=b)printf("N");if(a<=b)printf("X");return 0;}', b'-2147483648\n2147483647\n', b'LNX'),
}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--classes',type=Path,default=WORK/'classes')
    parser.add_argument('--baseline',type=Path,default=WORK/'baseline')
    parser.add_argument('--build',action='store_true')
    parser.add_argument('--require-gain',action='store_true')
    parser.add_argument('--array-ratio',type=float)
    parser.add_argument('--delay',action='store_true')
    parser.add_argument('--only')
    args=parser.parse_args()
    WORK.mkdir(parents=True,exist_ok=True)
    if args.build:
        args.classes.mkdir(parents=True,exist_ok=True)
        run(['javac','--release','17','-encoding','UTF-8','-d',str(args.classes),*map(str,(ROOT/'src').rglob('*.java'))],ROOT)
    records=[]
    for name,(source,data,expected) in CASES.items():
        if args.only and name != args.only: continue
        row={'case':name}
        for version,classes in [('baseline',args.baseline),('candidate',args.classes)]:
            directory=WORK/'cases'/name/version
            directory.mkdir(parents=True,exist_ok=True)
            (directory/'testfile.txt').write_text(source,encoding='utf-8')
            run(['java',*FLAGS,'-cp',str(classes),'Compiler'],directory)
            assert not (directory/'error.txt').read_text().strip(),name
            actual,stats=simulate(directory,data)
            assert actual==expected,(name,version,actual,expected)
            row[version]=stats['finalCycles']
            if args.delay and version=='candidate':
                assert simulate_delayed(directory,classes,data)==expected,name
        records.append(row)
        print(row,flush=True)
    (WORK/'performance.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
    if args.require_gain:
        for row in records:
            if row['case'] in ('count-only','constant-sum','nested-sum','main-only-global'):
                assert row['candidate']<row['baseline']*0.25,row
            if row['case']=='array-loop': assert row['candidate']<row['baseline']*0.85,row
            if row['case']=='branch-inline': assert row['candidate']<row['baseline']*0.6,row
    if args.array_ratio is not None:
        row=next(r for r in records if r['case']=='array-loop')
        assert row['candidate']<row['baseline']*args.array_ratio,row
    print('All loop regression outputs passed')

if __name__=='__main__':main()
