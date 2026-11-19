"""First-batch regression: real Compiler -> course MARS; no frontend changes."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import random
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'tests' / '.phase1-work'
MARS = Path(os.environ.get('MARS_JAR', 'D:/DevTools/MARS/mars-2026-BUAA.jar'))
CLANG = Path(os.environ.get('CLANG_EXE', 'D:/DevTools/LLVM/bin/clang.exe'))
FLAGS = ['-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '-XX:ActiveProcessorCount=1',
         '-Dfile.encoding=UTF-8', '-Djava.awt.headless=true']
CASES = {
    'constant': ('int main(){int a=6;int b=a*7;printf("%d",b);return 0;}', '', '42'),
    'join_equal': ('int main(){int n=get_int();int x;if(n>0)x=7;else x=7;printf("%d",x*6);return 0;}', '-1\n', '42'),
    'join_different': ('int main(){int n=get_int();int x;if(n>0)x=7;else x=9;printf("%d",x*6);return 0;}', '-1\n', '54'),
    'loop': ('int main(){int n=get_int();int i=0;int s=0;while(i<n){if(i%2==0)s=s+i;else s=s-i;i=i+1;}printf("%d %d",s,i);return 0;}', '20\n', '-10 20'),
    'zero_loop': ('int main(){int n=get_int();int i=0;int s=17;while(i<n){s=s+i;i=i+1;}printf("%d",s);return 0;}', '0\n', '17'),
    'alias': ('int f(int a[],int b[]){a[0]=7;b[0]=9;return a[0];}int main(){int a[2]={1,2};printf("%d %d",f(a,a),a[0]);return 0;}', '', '9 9'),
    'global_call': ('int g=1;int f(){g=g+2;return g;}int main(){int x=g;int y=f();printf("%d %d %d",x,y,g);return 0;}', '', '1 3 3'),
    'recursion': ('int f(int n){if(n<2)return n;return f(n-1)+f(n-2);}int main(){int n=get_int();printf("%d",f(n));return 0;}', '12\n', '144'),
    'dead_branch': ('int main(){int x=3;if(x==3)printf("yes");else printf("no");return 0;}', '', 'yes'),
    'dead_division': ('int main(){if(0)printf("%d",1/0);printf("ok");return 0;}', '', 'ok'),
    'side_effect': ('int f(){printf("a");return 2;}int main(){int x=f();x=0;printf("%d",x);return 0;}', '', 'a0'),
    'dead_store': ('int main(){int n=get_int();int x=n*3;x=9;printf("%d",x);return 0;}', '5\n', '9'),
    'gvn': ('int main(){int n=get_int();int m=get_int();printf("%d %d %d %d",n+m,m+n,n-m,m-n);return 0;}', '8\n3\n', '11 11 5 -5'),
    'sibling': ('int main(){int n=get_int();int m=get_int();int x;if(n>0)x=m+2;else x=m+3;printf("%d %d",x,m+2);return 0;}', '-1\n5\n', '8 7'),
    'division': ('int main(){int a=get_int();printf("%d %d %d %d",a/7,a%7,a/(-8),a%(-8));return 0;}', '-2147483648\n', '-306783378 -2 268435456 0'),
    'comparisons': ('int main(){int n=get_int();if(n<3)printf("a");if(n>3)printf("b");if(n==3)printf("c");if(n!=3)printf("d");return 0;}', '2\n', 'ad'),
    'nested': ('int main(){int n=get_int();int i=0;int j=0;int s=0;while(i<n){j=0;while(j<i){s=s+j;j=j+1;}i=i+1;}printf("%d",s);return 0;}', '5\n', '10'),
    'continue_break': ('int main(){int i=0;int s=0;while(i<10){i=i+1;if(i==3)continue;if(i==7)break;s=s+i;}printf("%d %d",s,i);return 0;}', '', '18 7'),
    'dead_void_function': ('void unused(){return;}int main(){printf("ok");return 0;}', '', 'ok'),
    'dead_void_return': ('void f(){if(0)return;printf("ok");return;}int main(){f();return 0;}', '', 'ok'),
    'dead_recursive_function': ('int unused(int n){return unused(n+1);}int main(){printf("ok");return 0;}', '', 'ok'),
    'same_operand': ('int main(){int x=get_int();int y=x+x;printf("%d %d",y,y-y);return 0;}', '7\n', '14 0'),
}

def run(command, cwd, data=b'', timeout=60):
    r = subprocess.run(list(map(str, command)), cwd=cwd, input=data, capture_output=True, timeout=timeout)
    if r.returncode:
        raise AssertionError((r.returncode, r.stderr.decode(errors='replace')[-2000:], r.stdout[-800:]))
    return r

def build(source, classes):
    classes.mkdir(parents=True, exist_ok=True)
    files = list(source.rglob('*.java')) + [ROOT / 'tests/OptimizationProbe.java']
    argfile = classes / 'sources.txt'
    argfile.write_text('\n'.join('"' + str(f).replace('\\', '/') + '"' for f in files), encoding='utf-8')
    run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', classes, '@' + str(argfile)], ROOT)

def public_cases():
    cases = {}
    for archive in Path.home().joinpath('Downloads').glob('2026*代码生成*.zip'):
        group = re.search(r'-([ABC])', archive.name).group(1)
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if name.endswith('/testfile.txt'):
                    base = name.rsplit('/', 1)[0] + '/'
                    cases['public_' + group + '_' + base.split('/')[-2]] = (
                        z.read(name).decode('utf-8-sig'), z.read(base+'in.txt').decode('utf-8-sig'),
                        z.read(base+'ans.txt').decode('utf-8-sig').rstrip())
    return cases

def fuzz_cases():
    rng = random.Random(20261118)
    cases = {}
    for index in range(24):
        n, a, b = rng.randrange(13), rng.randrange(-20,21), rng.randrange(-20,21)
        initial = (n,a,b)
        total = 0
        for i in range(n):
            if i % 3 == 0: a,b = b,a
            elif a < b: a = a+i
            else: b = b-i
            total += a-b
        source = '''int main(){int n=get_int();int a=get_int();int b=get_int();int i=0;int s=0;
while(i<n){if(i%3==0){int t=a;a=b;b=t;}else if(a<b)a=a+i;else b=b-i;s=s+a-b;i=i+1;}
printf("%d %d %d %d",a,b,s,i);return 0;}'''
        cases[f'fuzz_{index:02}'] = (source, ''.join(str(v)+'\n' for v in initial), f'{a} {b} {total} {n}')
    return cases

def verify_one(classes, name, fixture, mode, label, check_ir, llvm, properties=()):
    source, data, expected = fixture
    folder = WORK / label / name / mode
    folder.mkdir(parents=True, exist_ok=True)
    (folder/'testfile.txt').write_text(source, encoding='utf-8')
    record = {'case': name, 'mode': mode}
    try:
        run(['java', *FLAGS, '-Dcompiler.optimize='+mode, *properties, '-cp', classes, 'OptimizationProbe'], folder)
        errors = (folder/'error.txt').read_text(encoding='utf-8')
        assert not errors.strip(), ('frontend', errors)
        if llvm:
            run([CLANG, '-Wno-override-module', '-c', '-x', 'ir', 'optimized.ll', '-o', 'verified.obj'], folder)
            record['llvm_verified'] = True
        r = run(['java', *FLAGS, '-jar', MARS, 'mips.txt'], folder, data.encode(), timeout=90)
        output = r.stdout.decode('utf-8', errors='replace').replace('\r\n', '\n')
        assert output.startswith('MARS 2026-BUAA'), output[:500]
        output = output.split('\n', 1)[1].lstrip('\n').rstrip()
        assert not r.stderr.strip(), r.stderr
        assert output == expected.replace('\r\n', '\n').rstrip(), (output[:1000], expected[:1000])
        statistics = json.loads((folder/'instruction_statistics.json').read_text())
        record['cycles'] = statistics['finalCycles']
        record['memory'] = statistics['items']['Memory']['instructionCount']
        record['ir_instructions'] = len(re.findall(r'^\s+(?:%|store |ret |br |call )', (folder/'optimized.ll').read_text(), re.MULTILINE))
        if check_ir and mode == 'on':
            ir = (folder/'optimized.ll').read_text()
            if name in ('constant', 'join_equal', 'dead_store'):
                assert not re.search(r'\b(load|mul)\b', ir), 'constant propagation left load/mul'
            if name == 'dead_branch':
                assert 'br i1' not in ir, 'constant branch remains'
        record['passed'] = True
    except Exception as error:
        record.update(passed=False, error=str(error))
    return record

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--label', default='current')
    p.add_argument('--source', type=Path, default=ROOT/'src')
    p.add_argument('--public', action='store_true')
    p.add_argument('--check-ir', action='store_true')
    p.add_argument('--llvm', action='store_true')
    p.add_argument('--fuzz', action='store_true')
    p.add_argument('--only', default='')
    args = p.parse_args()
    classes = WORK/args.label/'classes'
    build(args.source, classes)
    cases = dict(CASES)
    if args.public: cases.update(public_cases())
    if args.fuzz: cases.update(fuzz_cases())
    if args.only: cases = {k:v for k,v in cases.items() if re.search(args.only,k)}
    jobs = [(n,c,m) for n,c in cases.items() for m in ('off','on')]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        records = list(pool.map(lambda x: verify_one(classes,*x,args.label,args.check_ir,args.llvm), jobs))
    (WORK/args.label/'results.json').write_text(json.dumps(records,indent=2,ensure_ascii=False),encoding='utf-8')
    failures = [r for r in records if not r['passed']]
    for r in failures: print(json.dumps(r,ensure_ascii=True))
    print(f'{len(records)-len(failures)}/{len(records)} passed; {WORK/args.label/"results.json"}',flush=True)
    raise SystemExit(bool(failures))
