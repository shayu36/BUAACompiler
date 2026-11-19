"""Independent arithmetic and function oracles for the rewritten optimizations."""
import random

def wrap(n): return (n+2**31)%2**32-2**31

def arithmetic_case():
    rng=random.Random(24373313)
    divisors=[1,-1,2,-2,3,-3,7,-7,8,-8,31,-31,65535,-65535,998244353,-998244353,1073741823,1073741824,2147483647,-2147483647,-2147483648]
    multipliers=[0,1,-1,2,-2,3,-3,5,-5,7,-7,9,-9,15,-15,16,-16,65536,-2147483648]
    values=[-2147483648,-2147483647,-1073741824,-9,-8,-7,-1,0,1,7,8,9,1073741824,2147483646,2147483647]+[rng.randrange(-2**31,2**31) for _ in range(30)]
    literal=lambda n:'(-2147483647-1)' if n==-2**31 else f'({n})'
    body=''.join(f'printf("%d %d\\n",x/{literal(d)},x%{literal(d)});' for d in divisors)
    body+=''.join(f'printf("%d\\n",x*{literal(d)});' for d in multipliers)
    source=f'int main(){{int i=0;while(i<{len(values)}){{int x=get_int();{body}i=i+1;}}return 0;}}'
    expected=[]
    for x in values:
        for d in divisors:
            q=abs(x)//abs(d)*(-1 if (x<0)!=(d<0) else 1)
            expected.append(f'{wrap(q)} {x-q*d}')
        expected.extend(str(wrap(x*d)) for d in multipliers)
    return source,'\n'.join(map(str,values))+'\n','\n'.join(expected)

CASES={
    'arithmetic_boundaries':arithmetic_case(),
    'constant_recursive':('int f(int n){if(n<2)return n;return f(n-1)+f(n-2);}int main(){printf("%d",f(25));return 0;}','','75025'),
    'constant_loop_call':('int f(int n){int i=0;int s=3;while(i<n){s=s*3+i;i=i+1;}return s;}int main(){printf("%d",f(7));return 0;}','','7104'),
    'memo_collision':('int f(int n,int b){if(n<2)return n+b;return f(n-1,b)+f(n-2,b);}int main(){int n=get_int();printf("%d %d %d",f(n,5),f(n,261),f(n,5));return 0;}','8\n','191 8895 191'),
    'impure_recursion':('int g=0;int f(int n){g=g+1;if(n<2)return n;return f(n-1)+f(n-2);}int main(){int n=get_int();int a=f(n);int b=f(n);printf("%d %d %d",a,b,g);return 0;}','6\n','8 8 50'),
    'unexecuted_infinite':('int f(int n){while(n==0){n=n+0;}return n;}int main(){int n=get_int();if(n)printf("%d",f(0));printf("ok");return 0;}','0\n','ok'),
}

def loop_boundaries():
    cases={}
    limits=[(-3,9),(10,10),(15,3),(-2**31,-2**31+4),(2**31-5,2**31-1),(-2**31,2**31-1),(-2**31,0),(-1,2**31-1)]
    for direction,op,step in [('up','<',1),('down','>',-1),('ne','!=',1),('ne_down','!=',-1)]:
        source=f'int main(){{int start=get_int();int end=get_int();int i=start;int s=7;int t=9;while(i{op}end){{s=s+i;t=t+3;i=i+({step});}}printf("%d %d %d",i,s,t);return 0;}}'
        for k,(start,end) in enumerate(limits):
            if direction=='down':start,end=end,start
            n=(step*(end-start))%(2**32) if op=='!=' else max(0,step*(end-start))
            final=wrap(start+step*n)
            total=wrap(7+n*start+step*n*(n-1)//2)
            cases[f'boundary_{direction}_{k}']=(source,f'{start}\n{end}\n',f'{final} {total} {wrap(9+3*n)}')
    rng=random.Random(715)
    for k in range(20):
        count=rng.randrange(2,70); a=rng.randrange(-10,11); b=rng.randrange(-10,11)
        initial=(a,b)
        for i in range(count):
            a=wrap(a*3+b+i);b=wrap(b-a*2+1)
        source=f'int main(){{int a=get_int();int b=get_int();int i=0;while(i<{count}){{a=a*3+b+i;b=b-a*2+1;i=i+1;}}printf("%d %d %d",a,b,i);return 0;}}'
        cases[f'affine_random_{k}']=(source,f'{initial[0]}\n{initial[1]}\n',f'{a} {b} {count}')
    return cases

CASES.update(loop_boundaries())
