"""Rewritten advanced passes: independent outputs, with the full old compiler baseline."""
import argparse
import concurrent.futures
import json
import hashlib
from pathlib import Path
import re
import verify_phase1 as base
from audit_submitted_regression import CASES as AUDIT_CASES, OLD
from verify_loop_optimization import CASES as LOOP_CASES
from verify_phase2 import CASES as REGISTER_CASES, pressure_case
from phase3_cases import CASES as ADVANCED_CASES

WORK=base.ROOT/'tests/.phase3-work'

def source_hashes(root):
    return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.rglob('*.java'))}

def verify(classes,name,fixture,version,llvm):
    source,data,expected=fixture
    folder=WORK/'final'/version/name
    folder.mkdir(parents=True,exist_ok=True)
    (folder/'testfile.txt').write_text(source,encoding='utf-8')
    if isinstance(data,str):data=data.encode()
    if isinstance(expected,bytes):expected=expected.decode()
    record=dict(case=name,version=version)
    try:
        entry='OptimizationProbe' if version=='candidate' else 'Compiler'
        base.run(['java',*base.FLAGS,'-cp',classes,entry],folder)
        assert not (folder/'error.txt').read_text(encoding='utf-8').strip()
        if llvm and version=='candidate':
            base.run([base.CLANG,'-Wno-override-module','-c','-x','ir','optimized.ll','-o','verified.obj'],folder)
        output=base.run(['java',*base.FLAGS,'-jar',base.MARS,'mips.txt'],folder,data,timeout=10 if name.startswith('boundary_') else 120)
        actual=output.stdout.decode().replace('\r\n','\n').split('\n',1)[1].strip()
        assert actual==expected.strip(),(actual[:1000],expected[:1000])
        assert not output.stderr,output.stderr
        record.update(passed=True,cycles=json.loads((folder/'instruction_statistics.json').read_text())['finalCycles'])
    except Exception as error:record.update(passed=False,error=str(error))
    return record

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--only',default='')
    p.add_argument('--public',action='store_true')
    p.add_argument('--llvm',action='store_true')
    p.add_argument('--old',action='store_true')
    p.add_argument('--submitted',action='store_true')
    p.add_argument('--require-old-parity',action='store_true')
    args=p.parse_args()
    candidates={**base.CASES,**REGISTER_CASES,**AUDIT_CASES,**LOOP_CASES,**ADVANCED_CASES,**base.fuzz_cases(),'pressure':pressure_case()}
    if args.public:candidates.update(base.public_cases())
    if args.only:candidates={k:v for k,v in candidates.items() if re.search(args.only,k)}
    classes=WORK/'final/classes'
    manifest=source_hashes(base.ROOT/'src')
    base.build(base.ROOT/'src',classes)
    versions={'candidate':classes}
    if args.old:
        versions['historical']=WORK/'old-classes'
        base.build(OLD,versions['historical'])
    if args.submitted:
        audit=base.ROOT/'tests/.regression-audit-20261118'
        versions['submitted']=audit/'submitted-classes'
        base.build(audit/'submitted-src',versions['submitted'])
    # The submitted compiler cannot summarize billions of iterations. Its outputs
    # on small cases remain part of the comparison; large boundaries use an oracle.
    jobs=[(cl,n,c,v,args.llvm) for n,c in candidates.items() for v,cl in versions.items()
          if not (v=='submitted' and n.startswith('boundary_'))]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        records=list(pool.map(lambda job:verify(*job),jobs))
    assert source_hashes(base.ROOT/'src')==manifest,'Source changed during verification; rerun the final suite'
    (WORK/'final/results.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
    metadata=dict(source_hashes=manifest,java_release=17,mars=str(base.MARS),
                  mars_sha256=hashlib.sha256(base.MARS.read_bytes()).hexdigest(),
                  clang=str(base.CLANG),historical_source=str(OLD),
                  submitted_archive_sha256=hashlib.sha256((base.ROOT/'src/backend.zip').read_bytes()).hexdigest())
    (WORK/'final/metadata.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    failures=[r for r in records if not r['passed']]
    for r in failures:print(json.dumps(r,ensure_ascii=True),flush=True)
    print(f'{len(records)-len(failures)}/{len(records)} outputs passed',flush=True)
    if failures:raise SystemExit(1)
    regressions=[]
    if args.old:
        for name in candidates:
            pair={r['version']:r['cycles'] for r in records if r['case']==name}
            if pair['candidate']>pair['historical']:
                regressions.append(name)
                if len(regressions)<=12: print('OLD_BASELINE_REGRESSION',name,pair,flush=True)
        print(f'{len(regressions)}/{len(candidates)} candidates slower than full old baseline; details retained in results.json',flush=True)
    if args.require_old_parity:
        assert args.old,'Full old baseline is required'
        assert not regressions,('Do not recommend submission',regressions)
