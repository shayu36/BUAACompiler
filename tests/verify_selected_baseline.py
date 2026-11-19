"""Incremental optimization against the restored complete compiler, never a rewrite baseline."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import re
import verify_phase1 as base
from audit_submitted_regression import OLD, CASES as AUDIT
from verify_loop_optimization import CASES as LOOPS
from verify_phase2 import CASES as REGISTERS, pressure_case
from phase3_cases import CASES as ADVANCED
from memory_forward_cases import CASES as MEMORY

WORK=base.ROOT/'tests/.highscore-resume-20261118'

def hashes(source):
    return {p.relative_to(source).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(source.rglob('*.java'))}

def build(source,classes):
    classes.mkdir(parents=True,exist_ok=True)
    paths=sorted(source.rglob('*.java'))+[base.ROOT/'tests/SelectedBaselineProbe.java']
    argfile=classes/'sources.txt'
    argfile.write_text('\n'.join('"'+p.as_posix()+'"' for p in paths),encoding='utf-8')
    base.run(['javac','--release','17','-encoding','UTF-8','-d',classes,'@'+str(argfile)],base.ROOT)

def check(classes,name,fixture,version,label,llvm,properties):
    folder=WORK/label/version/name
    folder.mkdir(parents=True,exist_ok=True)
    source,data,expected=fixture
    if isinstance(data,bytes):data=data.decode()
    if isinstance(expected,bytes):expected=expected.decode()
    (folder/'testfile.txt').write_text(source,encoding='utf-8')
    record=dict(case=name,version=version)
    try:
        base.run(['java',*base.FLAGS,*properties,'-cp',classes,'SelectedBaselineProbe'],folder)
        assert not (folder/'error.txt').read_text(encoding='utf-8').strip()
        if llvm:base.run([base.CLANG,'-Wno-override-module','-c','-x','ir','optimized.ll','-o','verified.obj'],folder)
        result=base.run(['java',*base.FLAGS,'-jar',base.MARS,'mips.txt'],folder,data.encode(),timeout=15 if name.startswith('boundary_') else 120)
        actual=result.stdout.decode().replace('\r\n','\n').split('\n',1)[1].strip()
        assert actual==expected.strip(),(actual[:600],expected[:600])
        assert not result.stderr,result.stderr
        stats=json.loads((folder/'instruction_statistics.json').read_text())
        record.update(passed=True,cycles=stats['finalCycles'],memory=stats['items']['Memory']['instructionCount'],assembly_sha256=hashlib.sha256((folder/'mips.txt').read_bytes()).hexdigest())
    except Exception as error:record.update(passed=False,error=str(error))
    (folder/'verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
    return record

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--label',default='final')
    p.add_argument('--only',default='')
    p.add_argument('--full',action='store_true')
    p.add_argument('--llvm',action='store_true')
    p.add_argument('--fallback',action='store_true')
    p.add_argument('--require-gain',action='store_true')
    p.add_argument('--identical',action='store_true')
    p.add_argument('--source',type=Path,default=base.ROOT/'src')
    p.add_argument('--baseline',type=Path,default=OLD)
    p.add_argument('--work',type=Path,default=WORK)
    p.add_argument('--disable-option',action='append')
    p.add_argument('--gain-case',default='memory_disjoint_elements')
    p.add_argument('--gain-metric',choices=['memory','cycles'],default='memory')
    p.add_argument('--gain-ratio',type=float,default=0.7)
    p.add_argument('--divrem',action='store_true')
    p.add_argument('--copies',action='store_true')
    p.add_argument('--branches',action='store_true')
    p.add_argument('--leaf',action='store_true')
    p.add_argument('--peephole',action='store_true')
    p.add_argument('--small-multiply',action='store_true')
    p.add_argument('--leaf-arguments',action='store_true')
    args=p.parse_args()
    WORK=args.work
    fixtures={**AUDIT,**MEMORY}
    if args.full:fixtures={**base.CASES,**REGISTERS,**AUDIT,**LOOPS,**ADVANCED,**base.fuzz_cases(),'pressure':pressure_case(),**base.public_cases(),**MEMORY}
    if args.divrem:
        from divrem_cases import CASES as DIVREM
        fixtures.update(DIVREM)
    if args.copies:
        from divrem_cases import COPY_CASES
        fixtures.update(COPY_CASES)
    if args.branches:
        from branch_fusion_cases import CASES as BRANCHES
        fixtures.update(BRANCHES)
    if args.leaf:
        from leaf_return_cases import CASES as LEAF
        fixtures.update(LEAF)
    if args.peephole:
        from peephole_cases import CASES as PEEPHOLE
        fixtures.update(PEEPHOLE)
    if args.small_multiply:
        from small_multiply_cases import CASES as SMALL_MULTIPLY
        fixtures.update(SMALL_MULTIPLY)
    if args.leaf_arguments:
        from leaf_argument_cases import CASES as LEAF_ARGUMENTS
        fixtures.update(LEAF_ARGUMENTS)
    if args.only:fixtures={n:f for n,f in fixtures.items() if re.search(args.only,n)}
    manifest=hashes(args.source)
    versions={'baseline':(args.baseline,()),'candidate':(args.source,())}
    if args.fallback:versions['fallback']=(args.source,tuple('-D'+p for p in (args.disable_option or ['compiler.memoryForward=off'])))
    classes={}
    for version,(source,props) in versions.items():
        classes[version]=WORK/args.label/(version+'-classes')
        build(source,classes[version])
    (WORK/args.label/'source-manifest.json').write_text(json.dumps(dict(
        source=str(args.source),source_hashes=manifest,baseline=str(args.baseline),
        baseline_hashes=hashes(args.baseline),versions={v:list(props) for v,(_,props) in versions.items()}
    ),indent=2),encoding='utf-8')
    jobs=[(classes[v],name,fixture,v,args.label,args.llvm,props) for name,fixture in fixtures.items() for v,(_,props) in versions.items()]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        records=list(pool.map(lambda job:check(*job),jobs))
    assert hashes(args.source)==manifest,'Source changed during verification'
    report=dict(source=str(args.source),baseline=str(args.baseline),source_hashes=manifest,records=records)
    (WORK/args.label/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    failures=[r for r in records if not r['passed']]
    for r in failures:print(json.dumps(r,ensure_ascii=True),flush=True)
    print(f'{len(records)-len(failures)}/{len(records)} executions passed',flush=True)
    assert not failures,'Output or IR validation failed'
    regressions=[]
    for name in fixtures:
        pair={r['version']:r for r in records if r['case']==name}
        if args.identical:assert pair['baseline']['assembly_sha256']==pair['candidate']['assembly_sha256'],name
        if args.fallback:assert pair['baseline']['assembly_sha256']==pair['fallback']['assembly_sha256'],(name,'fallback changed')
        if pair['candidate']['cycles']>pair['baseline']['cycles']:regressions.append({n:r['cycles'] for n,r in pair.items()}|{'case':name})
    print('Performance regressions:',len(regressions),flush=True)
    for r in regressions[:12]:print(r,flush=True)
    if args.require_gain:
        assert not regressions,regressions
        gain={r['version']:r for r in records if r['case']==args.gain_case}
        assert gain['candidate'][args.gain_metric]<gain['baseline'][args.gain_metric]*args.gain_ratio,'Target optimization gain not reached'
