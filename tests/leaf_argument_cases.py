"""Leaf parameters may stay in a0-a3; stack arguments must retain their offsets."""
def calc(n,d,s):
    for i in range(n):s=(s*s+i)%d
    return s
pointer_first=[(x*x+255+i)%997 for i,x in enumerate([2,4,8,16])]
pointer_second=[(x*x+129+i)%997 for i,x in enumerate(pointer_first)]
many_parameters=','.join(['int n']+[f'int p{i}' for i in range(1,20)])
many_expression='+'.join(f'p{i}*s' for i in range(1,20))
many_arguments=','.join(['n']+[str(i) for i in range(1,20)])
CASES={
 'leaf_argument_zero_trip':('int count=0;int calc('+many_parameters+'){int i=0;int s=0;while(i<n){s=(s*s+'
   +many_expression+'+1)%997;i=i+1;}count=count+1;return s;}int main(){int n=get_int();int s=calc('
   +many_arguments+');printf("%d %d",s,count);return 0;}', '0\n','0 1'),
 'leaf_argument_loop':('int count=0;int calc(int n,int d,int s){int i=0;while(i<n){s=(s*s+i)%d;i=i+1;}'
   'count=count+1;return s;}int main(){int n=get_int();int i=0;int total=0;'
   'while(i<n){total=total+calc(i%8+1,997,i);i=i+1;}printf("%d %d",total,count);return 0;}',
   '1000\n',f'{sum(calc(i%8+1,997,i) for i in range(1000))} 1000'),
 'leaf_argument_stack':('int count=0;int calc(int unused,int seed,int unused2,int d,int a,int b,int n,int last){'
   'int i=0;int s=seed+a+b+last;while(i<n){s=(s*s+i)%d;i=i+1;}count=count+1;return s;}'
   'int main(){int n=get_int();int x=get_int();printf("%d %d",calc(55,x,66,997,2,4,n,8),count);return 0;}',
   '7\n19\n',f'{calc(7,997,33)} 1'),
 'leaf_argument_pointer':('int count=0;int calc(char c,int data[],int n,int k){int i=0;int s=0;'
   'while(i<n){data[i]=(data[i]*data[i]+(int)c+i)%k;s=s+data[i];i=i+1;}count=count+1;return s;}'
   'int main(){int n=get_int();int a[4]={2,4,8,16};int x=calc((char)255,a,n,997);'
   'int y=calc((char)129,a,n,997);printf("%d %d %d %d",x,y,a[3],count);return 0;}',
   '4\n',f'{sum(pointer_first)} {sum(pointer_second)} {pointer_second[3]} 2'),
}
