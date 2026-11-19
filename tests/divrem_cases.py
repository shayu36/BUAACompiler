"""Independent output oracles for reusing a computed quotient in a remainder."""
def quotient(a,b):return (abs(a)//abs(b))*(-1 if (a<0)!=(b<0) else 1)
def remainder(a,b):return a-quotient(a,b)*b

CASES={
    'divrem_reuse':('int main(){int n=get_int();int d=get_int();int i=0;int s=0;while(i<n){s=s+i/d+i%d;i=i+1;}printf("%d",s);return 0;}','1000\n7\n',str(sum(quotient(i,7)+remainder(i,7) for i in range(1000)))),
    'divrem_negative':('int main(){int n=get_int();int d=get_int();int i=0;int s=0;while(i<n){int x=i-500;s=s+x/d+x%d;i=i+1;}printf("%d",s);return 0;}','1000\n-7\n',str(sum(quotient(i-500,-7)+remainder(i-500,-7) for i in range(1000)))),
    'divrem_changed_dividend':('int main(){int x=get_int();int d=get_int();int q=x/d;x=x+1;printf("%d %d",q,x%d);return 0;}','12\n7\n','1 6'),
    'divrem_changed_divisor':('int main(){int x=get_int();int d=get_int();int q=x/d;d=d+1;printf("%d %d",q,x%d);return 0;}','15\n7\n','2 7'),
    'divrem_branch':('int main(){int x=get_int();int d=get_int();int q=x/d;if(x>0)x=0-x;printf("%d %d",q,x%d);return 0;}','15\n7\n','2 -1'),
}

COPY_CASES={
    'copy_loop_control':('int main(){int n=get_int();int a[1000];int i=0;int s=0;while(i<n){a[i]=i*i;s=s+a[i];i=i+1;}printf("%d",s);return 0;}','1000\n',str(sum(i*i for i in range(1000)))),
    'copy_live_old_value':('int main(){int n=get_int();int a[1000];int i=0;int s=0;while(i<n){int old=i;i=i+1;a[old]=i;s=s+old+a[old];}printf("%d",s);return 0;}','1000\n','1000000'),
    'copy_branch_update':('int main(){int n=get_int();int i=0;int x=3;int s=0;int a[100];while(i<n){int old=x;if(i%2==0)x=x+3;else x=x+1;a[i]=old;s=s+a[i]+x;i=i+1;}printf("%d %d",s,x);return 0;}','20\n','940 43'),
}
