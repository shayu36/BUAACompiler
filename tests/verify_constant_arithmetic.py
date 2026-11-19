"""Check generated divisions against integer arithmetic over boundaries and seeded inputs."""
import random
import argparse
from pathlib import Path
from verify_optimization import WORK, FLAGS, run, simulate

parser = argparse.ArgumentParser()
parser.add_argument('--classes', type=Path, default=WORK / 'classes')
classes = parser.parse_args().classes

rng = random.Random(24373313)
divisors = [1, -1, 2, -2, 3, -3, 7, -7, 8, -8, 31, -31, 65535, -65535,
            998244353, -998244353, 1073741823, 1073741824, 2147483647, -2147483647, -2147483648]
values = [-2147483648, -2147483647, -1073741824, -9, -8, -7, -1, 0, 1, 7, 8, 9,
          1073741824, 2147483646, 2147483647] + [rng.randrange(-2147483648, 2147483648) for _ in range(30)]
expressions = ['-2147483647-1' if d == -2147483648 else str(d) for d in divisors]
body = '\n'.join(f'printf("%d %d\\n",x/({d}),x%({d}));' for d in expressions)
source = f'int main(){{int i=0;while(i<{len(values)}){{int x=get_int();{body}i=i+1;}}return 0;}}'
directory = WORK / 'constant-arithmetic'
directory.mkdir(parents=True, exist_ok=True)
(directory / 'testfile.txt').write_text(source, encoding='utf-8')
run(['java', *FLAGS, '-cp', str(classes), 'Compiler'], directory)
assert not (directory / 'error.txt').read_text().strip()
actual, _ = simulate(directory, ('\n'.join(map(str, values))+'\n').encode())
expected = []
for value in values:
    for divisor in divisors:
        quotient = abs(value) // abs(divisor)
        if (value < 0) != (divisor < 0): quotient = -quotient
        remainder = value - quotient * divisor
        quotient = (quotient + 2**31) % 2**32 - 2**31
        expected.append(f'{quotient} {remainder}')
assert actual.decode().splitlines() == expected
print(f'{len(values)*len(divisors)} quotient/remainder pairs passed')
