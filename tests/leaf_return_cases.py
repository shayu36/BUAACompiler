"""Calls remain after inlining; result oracles do not depend on the compiler."""
def fold(x,s=1):
    for i in range(x):s=(s*3+i)%997
    return s

CASES={
    'leaf_return_loop':(
        'int total=0;int leaf(int x){int i=0;int s=1;while(i<x){s=(s*3+i)%997;i=i+1;}'
        'total=total+s;return s;}int main(){int n=get_int();int i=0;int s=0;'
        'while(i<n){s=s+leaf(i%7+2);i=i+1;}printf("%d %d",s,total);return 0;}',
        '1000\n',f'{sum(fold(i%7+2) for i in range(1000))} {sum(fold(i%7+2) for i in range(1000))}'),
    'leaf_stack_arguments':(
        'int total=0;int leaf(int a,int b,int c,int d,int e,int f){int data[16];int i=0;'
        'int s=a+b+c+d+e+f;while(i<f){data[i]=(s*3+i)%997;s=data[i];i=i+1;}'
        'total=total+s;return s;}int main(){int n=get_int();int s=leaf(1,2,3,4,5,n);'
        'printf("%d %d",s,total);return 0;}', '6\n',f'{fold(6,21)} {fold(6,21)}'),
    'leaf_syscall_output':(
        'int total=0;void leaf(int n){int i=0;while(i<n){printf("[%d]",i*i);i=i+1;}total=total+n;}'
        'int main(){int n=get_int();leaf(n);leaf(n+1);printf("%d",total);return 0;}',
        '3\n','[0][1][4][0][1][4][9]7'),
    'leaf_nested_calls':(
        'int total=0;int leaf(int x){int i=0;int s=1;while(i<x){s=(s*3+i)%997;i=i+1;}total=total+s;return s;}'
        'int outer(int n){int i=0;int s=0;while(i<n){s=s+leaf(i%7+2);i=i+1;}return s;}'
        'int main(){int n=get_int();printf("%d %d",outer(n),total);return 0;}',
        '20\n',f'{sum(fold(i%7+2) for i in range(20))} {sum(fold(i%7+2) for i in range(20))}'),
}
