"""Same inputs/oracles; adapt only 2026 while/get_int syntax to 2025 for/getint."""
from pathlib import Path
import json
import re
from verify_optimization import run, simulate, FLAGS
from verify_loop_optimization import CASES, WORK

records=[]
for name,(source,data,expected) in CASES.items():
    if name in ('char-loop', 'inline-char'):
        continue  # The 2025 reference has a different type grammar.
    source=source.replace('get_int()', 'getint()')
    source=re.sub(r'while\((.*?)\)\{',r'for(;\1;){',source)
    directory=WORK/'reference'/name
    directory.mkdir(parents=True,exist_ok=True)
    (directory/'testfile.txt').write_text(source,encoding='utf-8')
    row={'case':name}
    try:
        run(['java',*FLAGS,'-cp',str(WORK/'salyut-classes'),'Compiler'],directory,timeout=30)
        if (directory/'error.txt').exists():
            assert not (directory/'error.txt').read_text().strip(),(directory/'error.txt').read_text()
        actual,stats=simulate(directory,data)
        assert actual==expected,(actual,expected)
        row.update(correct=True,finalCycles=stats['finalCycles'])
    except Exception as error:
        row.update(correct=False,error=str(error)[:1200])
    records.append(row)
    print(row,flush=True)
(WORK/'salyut-comparison.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
