"""Compile the exact submitted ZIP and historical backup in isolated directories."""
import concurrent.futures
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile
from verify_phase1 import ROOT, FLAGS, MARS, run

WORK=ROOT/'tests/.regression-audit-20261118'
OLD=Path('D:/RunIdea/mycomplier-optimized-backup-20261118/src')
ZIP=ROOT/'src/backend.zip'
CASES={
    'count_only': ('int main(){int n=get_int();int i=0;while(i<n){i=i+1;}printf("%d",i);return 0;}','100000\n','100000'),
    'constant_sum': ('int main(){int s=get_int();int i=0;while(i<100000){s=s+i;i=i+1;}printf("%d %d",s,i);return 0;}','7\n','704982711 100000'),
    'division_loop': ('int main(){int n=get_int();int i=0;int s=0;while(i<n){s=s+i/7+i%7;i=i+1;}printf("%d",s);return 0;}','1000\n',str(sum(i//7+i%7 for i in range(1000)))),
    'call_loop': ('int f(int n){return n*3+1;}int main(){int n=get_int();int i=0;int s=0;while(i<n){s=s+f(i);i=i+1;}printf("%d",s);return 0;}','1000\n','1499500'),
    'recursion': ('int f(int n){if(n<2)return n;return f(n-1)+f(n-2);}int main(){int n=get_int();printf("%d",f(n));return 0;}','18\n','2584'),
    'public_convolution': ((ROOT/'tests/testcase14/testfile.txt').read_text(encoding='utf-8'),'3\n1\n2\n3\n2\n4\n5\n','4: 4 13 22 15'),
}

def build(source,classes):
    classes.mkdir(parents=True,exist_ok=True)
    files=sorted(source.rglob('*.java'))
    args=classes/'sources.txt'
    args.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in files),encoding='utf-8')
    run(['javac','--release','17','-encoding','UTF-8','-d',classes,'@'+str(args)],ROOT)
    return len(files)

def execute(job):
    name,version=job
    source,data,expected=CASES[name]
    directory=WORK/'cases'/name/version
    directory.mkdir(parents=True,exist_ok=True)
    (directory/'testfile.txt').write_text(source,encoding='utf-8')
    classes=WORK/('old-classes' if version=='historical' else 'submitted-classes')
    options=['-Dcompiler.registers=off'] if version=='restart_phase1' else []
    # Ordinary entry point and default optimization flags, as used by the judge.
    run(['java',*FLAGS,*options,'-cp',classes,'Compiler'],directory)
    assert not (directory/'error.txt').read_text(encoding='utf-8').strip(),(name,version,'compile error')
    result=run(['java',*FLAGS,'-jar',MARS,'mips.txt'],directory,data.encode(),timeout=120)
    output=result.stdout.decode('utf-8').replace('\r\n','\n').split('\n',1)[1].strip()
    assert output==expected,(name,version,output,expected)
    assert not result.stderr,result.stderr
    stats=json.loads((directory/'instruction_statistics.json').read_text())
    return dict(case=name,version=version,passed=True,statistics=stats)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--require-no-regression',action='store_true')
    options=parser.parse_args()
    WORK.mkdir(exist_ok=True)
    target=WORK/'submitted-src'
    target.mkdir(exist_ok=True)
    with zipfile.ZipFile(ZIP) as z:
        for name in z.namelist():
            assert (target/name).resolve().is_relative_to(target.resolve()),name
        z.extractall(target)
        names={n for n in z.namelist() if n.endswith('.java')}
        current={str(p.relative_to(ROOT/'src')).replace('\\','/') for p in (ROOT/'src').rglob('*.java')}
        assert names==current
        assert all(z.read(name)==(ROOT/'src'/name).read_bytes() for name in names)
    source_counts={}
    for label,source in [('submitted',target),('old',OLD)]:
        source_counts[label]=build(source,WORK/(label+'-classes'))
    jobs=[(case,version) for case in CASES for version in ('historical','restart_phase1','submitted')]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        records=list(pool.map(execute,jobs))
    report=dict(archive=str(ZIP),archive_sha256=hashlib.sha256(ZIP.read_bytes()).hexdigest(),
                source_counts=source_counts,historical_backup=str(OLD),records=records)
    (ROOT/'docs/optimization-regression-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    for case in CASES:
        print(case,{r['version']:r['statistics']['finalCycles'] for r in records if r['case']==case},flush=True)
    print(f'{len(records)}/{len(records)} outputs passed; exact submitted ZIP recompiled',flush=True)
    if options.require_no_regression:
        regressions=[]
        for case in CASES:
            pair={r['version']:r['statistics']['finalCycles'] for r in records if r['case']==case}
            if pair['submitted']>pair['historical']:regressions.append(case)
        assert not regressions,('Below historical baseline; do not recommend submission',regressions)

if __name__=='__main__':main()
