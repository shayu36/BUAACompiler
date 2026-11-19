"""Extreme inclusive bounds must retain the nonterminating fallback, not return early."""
from pathlib import Path
import argparse
from verify_optimization import run,FLAGS
WORK=Path('D:/RunIdea/mycomplier-tail-20261115')
parser=argparse.ArgumentParser()
parser.add_argument('--classes',type=Path,default=WORK/'classes')
args=parser.parse_args()
for direction,op,step,bound in [('up','<=',1,2147483647),('down','>=',-1,-2147483648)]:
    source=f'int main(){{int end=get_int();int i=end;while(i{op}end){{i=i+({step});}}printf("WRONG");return 0;}}'
    for version,classes in [('baseline',Path('D:/RunIdea/mycomplier-branch-20261115/classes')),('candidate',args.classes)]:
        directory=WORK/'fallback'/direction/version;directory.mkdir(parents=True,exist_ok=True)
        (directory/'testfile.txt').write_text(source,encoding='utf-8')
        for delay in [[],['db']]:
            flags=['-Dcompiler.mips.delaySlots=true'] if delay else []
            run(['java',*FLAGS,*flags,'-cp',str(classes),'Compiler'],directory)
            assert not (directory/'error.txt').read_text().strip()
            result=run(['java',*FLAGS,'-jar','D:/Accepted/tools/MARS-2024.jar','nc','me','ae91','se92','sm','10000',*delay,'mips.txt'],directory,f'{bound}\n'.encode())
            assert not result.stdout.strip(),(direction,version,result.stdout,result.stderr)
            assert b'maximum' in result.stderr.lower(),result.stderr
    print(direction,'extreme inclusive bound retains nontermination in normal/delayed modes',flush=True)
