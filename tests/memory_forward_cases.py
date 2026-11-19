"""Independent expected outputs for memory forwarding and alias invalidation."""
CASES={
    'memory_disjoint_elements':('int f(int a[],int n){int i=0;int s=0;while(i<n){s=s+a[0];a[1]=i;s=s+a[0]+a[1];i=i+1;}return s;}int main(){int a[2];a[0]=get_int();a[1]=0;printf("%d %d",f(a,1000),a[1]);return 0;}','7\n',f'{sum(14+i for i in range(1000))} 999'),
    'memory_dynamic_index':('int f(int a[],int n){int i=0;int s=0;while(i<n){int x=a[0];a[i%2]=i+7;s=s+x+a[0];i=i+1;}return s;}int main(){int a[2]={2,3};printf("%d",f(a,5));return 0;}','','77'),
    'memory_call_clobber':('int change(int a[],int n){if(n>0)a[0]=n;return a[0];}int f(int a[],int n){int i=0;int s=0;while(i<n){int x=a[0];change(a,i+7);s=s+x+a[0];i=i+1;}return s;}int main(){int a[1]={2};printf("%d",f(a,5));return 0;}','','81'),
    'memory_byte':('int main(){char a[2];a[0]=(char)255;int n=get_int();int i=0;int s=0;while(i<n){int x=(int)a[0];a[1]=(char)(i+250);s=s+x+(int)a[0]+(int)a[1];i=i+1;}printf("%d %d",s,(int)a[1]);return 0;}','20\n',f'{sum(510+(250+i)%256 for i in range(20))} 13'),
    'memory_branch':('int main(){int a[2];a[0]=get_int();a[1]=3;int n=get_int();if(n)a[0]=9;else a[1]=7;printf("%d %d",a[0],a[1]);return 0;}','4\n0\n','4 7'),
}
for same in [False,True]:
    a=2;b=3;s=0
    for i in range(8):
        x=a;b=i+7
        if same:a=b
        s+=x+a
    source='int f(int a[],int b[],int n){int i=0;int s=0;while(i<n){int x=a[0];b[0]=i+7;s=s+x+a[0];i=i+1;}return s;}int main(){int a[1]={2};int b[1]={3};printf("%d",f(a,'+('a' if same else 'b')+',8));return 0;}'
    CASES['memory_alias_same' if same else 'memory_alias_distinct']=(source,'',str(s))
