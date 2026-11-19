"""Spills and constant operands that survive IR-level memory forwarding."""
declarations=''.join(f'int x{i}=get_int();' for i in range(24))
expression='+'.join(f'x{i}*x{i}' for i in range(24))
CASES={
    'peephole_byte_checksum':('int main(){int n=get_int();char data[26];int i=0;'
       'while(i<26){data[i]=(char)(i+65);i=i+1;}i=0;int s=0;'
       'while(i<n){s=s+(int)data[i%26];i=i+1;}printf("%d",s);return 0;}',
       '1000\n',str(sum(65+i%26 for i in range(1000)))),
    'peephole_spill_square':('int main(){'+declarations+'printf("%d",'+expression+');return 0;}',
                            ''.join(f'{i+1}\n' for i in range(24)),str(sum(i*i for i in range(1,25)))),
    'peephole_shared_constant':('int main(){int n=get_int();int a[100];int i=0;while(i<n){'
       'a[i]=i*37+37;i=i+1;}i=0;int s=0;while(i<n){s=s+a[i];i=i+1;}printf("%d",s);return 0;}',
       '100\n',str(sum(i*37+37 for i in range(100)))),
}
