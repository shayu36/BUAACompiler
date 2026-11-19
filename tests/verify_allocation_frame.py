"""Six incoming arguments remain correct after a large leaf frame changes layout."""
import json
from pathlib import Path
import verify_selected_baseline as verification
from leaf_return_cases import CASES

verification.WORK=Path(r'D:\mycomplier\tests\.allocation-d83bc231')
work=verification.WORK
source,data,_=CASES['leaf_stack_arguments']
source=source.replace('data[16]','data[20000]').replace(
    'total=total+s;return s;', 's=s+data[0];total=total+s;return s;')
records=[]
for version,properties in [('baseline',()),('candidate',()),
        ('fallback',('-Dcompiler.smallMultiply=off','-Dcompiler.leafArguments=off'))]:
    record=verification.check(work/'final'/(version+'-classes'),'leaf_large_frame',
                              (source,data,'596 596'),version,'large-frame',True,properties)
    records.append(record)
    assert record['passed'],record
assert records[0]['assembly_sha256']==records[2]['assembly_sha256']
assert records[1]['cycles']<=records[0]['cycles']
(work/'large-frame/results.json').write_text(json.dumps(records,indent=2))
print([(r['version'],r['cycles']) for r in records])
