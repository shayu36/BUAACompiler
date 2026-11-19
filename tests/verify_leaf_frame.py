"""Leaf return-address removal must also remove its large-offset address setup."""
import json
from pathlib import Path
import verify_selected_baseline as verification
from leaf_return_cases import CASES

verification.WORK=Path(r'D:\mycomplier\tests\.backend-dfdace68')
work=verification.WORK
source,data,_=CASES['leaf_stack_arguments']
source=source.replace('data[16]','data[20000]').replace(
    'total=total+s;return s;', 's=s+data[0];total=total+s;return s;')
fixture=(source,data,'596 596')
records=[]
for version,properties in [('baseline',()),('candidate',()),
        ('fallback',('-Dcompiler.branchFusion=off','-Dcompiler.leafReturn=off'))]:
    record=verification.check(work/'final'/(version+'-classes'),'leaf_large_frame',
                              fixture,version,'leaf-extra',True,properties)
    records.append(record)
    assert record['passed'],record
    assert '800' in (work/'leaf-extra'/version/'leaf_large_frame/mips.txt').read_text()
assert records[0]['assembly_sha256']==records[2]['assembly_sha256']
assert records[1]['cycles']<records[0]['cycles']
(work/'leaf-extra/results.json').write_text(json.dumps(records,indent=2))
print('Large frame:',[(r['version'],r['cycles']) for r in records])
