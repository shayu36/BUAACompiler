"""Verify the actual submission archive with the ordinary Compiler entry point."""
import concurrent.futures
import argparse
import hashlib
import json
import zipfile
from pathlib import Path
import verify_phase1 as base
from audit_submitted_regression import CASES as AUDIT
from memory_forward_cases import CASES as MEMORY
from verify_selected_baseline import WORK, build, hashes

p=argparse.ArgumentParser()
p.add_argument('--work',type=Path,default=WORK)
p.add_argument('--divrem',action='store_true')
p.add_argument('--branches',action='store_true')
p.add_argument('--leaf',action='store_true')
p.add_argument('--peephole',action='store_true')
p.add_argument('--small-multiply',action='store_true')
p.add_argument('--leaf-arguments',action='store_true')
args=p.parse_args()
WORK=args.work

archive=base.ROOT/'src/backend.zip'
report=json.loads((WORK/'final/results.json').read_text(encoding='utf-8'))
assert hashes(base.ROOT/'src')==report['source_hashes'],'Unverified source changes'
assert archive.read_bytes()==(base.ROOT/'submission.zip').read_bytes(),'The two submission paths differ'
target=WORK/'package/src'
target.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(archive) as z:
    assert len(z.namelist())==len(set(z.namelist())),'Duplicate entries'
    assert set(z.namelist())==set(report['source_hashes'])|{'config.json'},'Unexpected archive contents'
    for name in z.namelist():
        assert (target/name).resolve().is_relative_to(target.resolve()),name
        assert z.read(name)==(base.ROOT/'src'/name).read_bytes(),name
    z.extractall(target)
assert hashes(target)==report['source_hashes']
classes=WORK/'package/classes'
build(target,classes)
expected_asm={r['case']:r['assembly_sha256'] for r in report['records'] if r['version']=='candidate'}

def verify(item):
    name,(source,data,expected)=item
    folder=WORK/'package/cases'/name
    folder.mkdir(parents=True,exist_ok=True)
    (folder/'testfile.txt').write_text(source,encoding='utf-8')
    base.run(['java',*base.FLAGS,'-cp',classes,'Compiler'],folder)
    assert not (folder/'error.txt').read_text(encoding='utf-8').strip(),name
    base.run([base.CLANG,'-Wno-override-module','-c','-x','ir','llvm_ir.txt','-o','verified.obj'],folder)
    r=base.run(['java',*base.FLAGS,'-jar',base.MARS,'mips.txt'],folder,data.encode())
    assert r.stdout.decode().split('\n',1)[1].strip()==expected.strip(),(name,r.stdout)
    assert not r.stderr,(name,r.stderr)
    assert hashlib.sha256((folder/'mips.txt').read_bytes()).hexdigest()==expected_asm[name],(name,'Package output differs')
    return dict(case=name,passed=True,assembly_identical=True)

fixtures={**AUDIT,**MEMORY}
if args.divrem:
    from divrem_cases import CASES as DIVREM, COPY_CASES
    fixtures.update(DIVREM)
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
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    rows=list(pool.map(verify,fixtures.items()))
metadata=dict(path=str(archive),sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),
              source_files=len(report['source_hashes']),entries=len(report['source_hashes'])+1,
              ordinary_entry='Compiler',records=rows)
(WORK/'package/verification.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'{len(rows)}/{len(rows)} archive executions passed; generated assembly matches verified source',flush=True)
print('SHA256',metadata['sha256'],flush=True)
