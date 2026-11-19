"""The course target disables delay slots; an explicit debug mode retains them."""
import argparse
from pathlib import Path
from verify_optimization import FLAGS, run, simulate

parser = argparse.ArgumentParser()
parser.add_argument('--classes', type=Path, required=True)
args = parser.parse_args()
work = Path('D:/RunIdea/mycomplier-nodelay-20261115/delay-mode')
source = '''int g; int f(int n){g=g+n;if(n<0)return -n;return n+1;}
int main(){int n=get_int();int i=0;int s=0;while(i<n){s=s+f(i-2);i=i+1;}
printf("%d %d",s,g);return 0;}'''
cycles = {}
for mode in ('course', 'delayed'):
    directory = work/mode
    directory.mkdir(parents=True, exist_ok=True)
    (directory/'testfile.txt').write_text(source, encoding='utf-8')
    flags = ['-Dcompiler.mips.delaySlots=true'] if mode=='delayed' else []
    run(['java', *FLAGS, *flags, '-cp', str(args.classes), 'Compiler'], directory)
    assert not (directory/'error.txt').read_text().strip()
    assembly = (directory/'mips.txt').read_text(encoding='utf-8')
    assert any(line.strip()=='nop' for line in assembly.splitlines()) == (mode=='delayed'), mode
    actual, stats = simulate(directory, b'6\n')
    assert actual == b'13 3', (mode, actual)
    cycles[mode] = stats['finalCycles']
    if mode=='delayed':
        result = run(['java', *FLAGS, '-jar', 'D:/Accepted/tools/MARS-2024.jar',
                      'nc', 'me', 'ae91', 'se92', 'db', 'sm', 'mips.txt'], directory, b'6\n')
        assert not result.stderr.strip() and result.stdout.rstrip()==actual
assert cycles['course'] < cycles['delayed'], cycles
print('Course and delayed debug targets passed:', cycles)
