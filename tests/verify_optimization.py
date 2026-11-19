"""Run the compiler's normal entry point and compare both modes in course MARS."""
import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORK = Path('D:/RunIdea/mycomplier-optimize-20261115')
MARS = Path('C:/Users/lenovo/Downloads/mars-2026-BUAA.jar')
FLAGS = ['-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-XX:ActiveProcessorCount=1',
         '-Dfile.encoding=UTF-8', '-Djava.awt.headless=true']
PREFIX = 'testfile14' + '24373313' + '贾鑫洋'
CASES = {
    'constant': ('int main(){int a=6; int b=a*7; printf("%d",b); return 0;}', b'', b'42'),
    'alias': ('''int set(int a[], int b[]){a[0]=7;b[0]=9;return a[0];}
int main(){int a[2]={1,2};printf("%d %d",set(a,a),a[0]);return 0;}''', b'', b'9 9'),
    'recursion': ('''int f(int a,int b,int c,int d,int e,int n){
if(n==0)return a+b+c+d+e;int x=f(a+1,b+2,c+3,d+4,e+5,n-1);return x+a+b+c+d+e;}
int main(){printf("%d",f(1,2,3,4,5,3));return 0;}''', b'', b'150'),
    'loop': ('''int main(){int n=get_int();int i=0;int s=0;
while(i<n){if(i%2==0)s=s+i;else s=s-i;i=i+1;}printf("%d",s);return 0;}''', b'20\n', b'-10'),
    'division': ('''int main(){int a=get_int();printf("%d %d %d %d %d %d",a/7,a%7,a/(-8),a%(-8),a/998244353,a%998244353);return 0;}''', b'-2147483648\n', b'-306783378 -2 268435456 0 -2 -150994942'),
}

def run(args, cwd, data=b'', timeout=90):
    result = subprocess.run(args, cwd=cwd, input=data, capture_output=True, timeout=timeout)
    if result.returncode:
        raise AssertionError(f'{args}: {result.stderr.decode(errors="replace")} {result.stdout[:800]!r}')
    return result

def simulate(directory, data, filename='mips.txt'):
    result = run(['java', *FLAGS, '-jar', str(MARS), filename], directory, data)
    banner = b'MARS 2026-BUAA  Copyright 2003-2014 Pete Sanderson and Kenneth Vollmar\n\r\n'
    assert result.stdout.startswith(banner), result.stdout[:1000]
    assert not result.stderr.strip(), result.stderr
    output = result.stdout.removeprefix(banner).replace(b'\r\n', b'\n').rstrip()
    assert b'Error in ' not in output and b'Error:' not in output, output[:1000]
    stats = json.loads((directory / 'instruction_statistics.json').read_text())
    return output, stats


def simulate_delayed(directory, classes, data):
    """Compile the explicit debug target separately from the course target."""
    target = directory / 'delay-check'
    target.mkdir(parents=True, exist_ok=True)
    (target / 'testfile.txt').write_bytes((directory / 'testfile.txt').read_bytes())
    run(['java', *FLAGS, '-Dcompiler.mips.delaySlots=true', '-cp', str(classes), 'Compiler'], target)
    assert not (target / 'error.txt').read_text().strip()
    result = run(['java', *FLAGS, '-jar', 'D:/Accepted/tools/MARS-2024.jar',
                  'nc', 'me', 'ae91', 'se92', 'db', 'sm', 'mips.txt'], target, data)
    assert not result.stderr.strip(), result.stderr
    return result.stdout.replace(b'\r\n', b'\n').rstrip()

def verify(classes):
    records = []
    for name, (source, data, expected) in CASES.items():
        for mode in ('off', 'on'):
            directory = WORK / 'regressions' / name / mode
            directory.mkdir(parents=True, exist_ok=True)
            (directory / 'testfile.txt').write_text(source, encoding='utf-8')
            run(['java', *FLAGS, f'-Dcompiler.optimize={mode}', '-cp', str(classes), 'Compiler'], directory)
            assert not (directory / 'error.txt').read_text().strip(), (name, 'compile errors')
            for stage in ('前', '后'):
                for kind in ('中间', '目标'):
                    path = directory / f'{PREFIX}_优化{stage}{kind}代码.txt'
                    assert path.exists(), f'Missing optimization artifact {path.name}'
                    path.read_text(encoding='utf-8')
            selected = '前' if mode == 'off' else '后'
            assert (directory / 'mips.txt').read_bytes() == (directory / f'{PREFIX}_优化{selected}目标代码.txt').read_bytes()
            assert (directory / 'llvm_ir.txt').read_bytes() == (directory / f'{PREFIX}_优化{selected}中间代码.txt').read_bytes()
            actual, stats = simulate(directory, data)
            assert actual == expected, (name, mode, actual, expected)
            records.append({'case': name, 'mode': mode, 'statistics': stats})
    constant_case = WORK / 'regressions/constant/on'
    assert ' mul ' not in (constant_case / f'{PREFIX}_优化后中间代码.txt').read_text(encoding='utf-8')
    for index in range(0, len(records), 2):
        assert records[index + 1]['statistics']['finalCycles'] < records[index]['statistics']['finalCycles']
    (WORK / 'regression-results.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
    print(f'{len(records)} optimization regression executions passed')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--classes', type=Path, default=WORK / 'classes')
    verify(parser.parse_args().classes)
