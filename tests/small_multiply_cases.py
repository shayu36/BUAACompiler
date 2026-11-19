"""Two-instruction constant products with independent modular integer oracles."""
def wrap(n):return (n+2**31)%2**32-2**31
VALUES=[-2**31,-2**31+1,-123456789,-65537,-1,0,1,65537,123456789,2**31-1]
FACTORS=sorted({wrap((1<<k)+1) for k in range(1,32)} |
               {wrap((1<<k)-1) for k in range(1,32)} |
               {wrap(1-(1<<k)) for k in range(1,32)} |
               {-9,-5,6,10,12345,-2**31})
lit=lambda n:'(-2147483647-1)' if n==-2**31 else f'({n})'
prints=''.join(f'printf("%d ",x*{lit(c)});' for c in FACTORS)
byte_values=[0,1,127,128,255,-1]
byte_factors=[3,7,31,127,129,255]
byte_prints=''.join(f'printf("%d ",(int)(x*(char){c}));' for c in byte_factors)
CASES={
 'small_multiply_byte':('int main(){int n=get_int();int i=0;while(i<n){char x=(char)get_int();'+byte_prints+'i=i+1;}return 0;}',
                       str(len(byte_values))+'\n'+'\n'.join(map(str,byte_values))+'\n',
                       ' '.join(str((x*c)&255) for x in byte_values for c in byte_factors)),
 'small_multiply_boundaries':('int main(){int n=get_int();int i=0;while(i<n){int x=get_int();'+prints+'i=i+1;}return 0;}',
                              str(len(VALUES))+'\n'+'\n'.join(map(str,VALUES))+'\n',
                              ' '.join(str(wrap(x*c)) for x in VALUES for c in FACTORS)),
 'small_multiply_loop':('int main(){int n=get_int();int a[1000];int i=0;while(i<n){'
                       'a[i]=i*31+(i%7)*7;i=i+1;}i=0;int s=0;while(i<n){s=s+a[i];i=i+1;}'
                       'printf("%d",s);return 0;}', '1000\n',str(sum(i*31+(i%7)*7 for i in range(1000)))),
 'small_multiply_negative':('int main(){int n=get_int();int a[1000];int i=0;while(i<n){'
                           'a[i]=(i-500)*(-31);i=i+1;}i=0;int s=0;while(i<n){s=s+a[i];i=i+1;}'
                           'printf("%d",s);return 0;}', '1000\n',str(sum((i-500)*(-31) for i in range(1000)))),
}
