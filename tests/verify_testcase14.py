"""Public program, deterministic inputs, independent modular-convolution oracle."""
import json
import random
import argparse
from pathlib import Path
from verify_optimization import ROOT, WORK, FLAGS, PREFIX, run, simulate

parser = argparse.ArgumentParser()
parser.add_argument('--classes', type=Path, default=WORK / 'classes')
args = parser.parse_args()
directory = WORK / 'testcase14'
directory.mkdir(parents=True, exist_ok=True)
(directory / 'testfile.txt').write_bytes((ROOT / 'tests/testcase14/testfile.txt').read_bytes())
run(['java', *FLAGS, '-cp', str(args.classes), 'Compiler'], directory)
assert not (directory / 'error.txt').read_text().strip()
rng = random.Random(20261115)
cases = [([2], [3]), ([1, 2, 3], [4, 5]), ([0, 0], [0, 0]),
         ([rng.randrange(10000) for _ in range(17)], [rng.randrange(10000) for _ in range(23)])]
records = []
for index, (left, right) in enumerate(cases):
    data = ('\n'.join(map(str, [len(left), *left, len(right), *right])) + '\n').encode()
    expected = [0] * (len(left) + len(right) - 1)
    for i, a in enumerate(left):
        for j, b in enumerate(right):
            expected[i+j] = (expected[i+j] + a*b) % 998244353
    expected_output = (str(len(expected)) + ':' + ''.join(' ' + str(n) for n in expected)).encode()
    record = {'input': index, 'n': len(left), 'm': len(right)}
    for stage in ('前', '后'):
        actual, stats = simulate(directory, data, f'{PREFIX}_优化{stage}目标代码.txt')
        assert actual == expected_output, (index, stage, actual, expected_output)
        record[stage] = stats
    records.append(record)
    print(index, record['前']['finalCycles'], record['后']['finalCycles'], flush=True)
(WORK / 'testcase14-results.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
print('Public testcase14 passed on four inputs, both modes')
