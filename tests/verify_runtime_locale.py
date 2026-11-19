"""Check Java filesystem encoding through Compiler.main in Linux UTF-8 and C locales.

On Windows this uses the installed Ubuntu WSL distribution. Compile the source to
the classes directory first, then pass that directory with --classes.
"""
import argparse
import os
import subprocess
import sys
from pathlib import Path

from verify_optimization import ROOT, WORK, PREFIX, simulate


def linux_path(path):
    path = path.resolve()
    if os.name == 'nt':
        return '/mnt/' + path.drive[0].lower() + path.as_posix()[2:]
    return str(path)


def verify(classes):
    for locale in ('C.UTF-8', 'C'):
        directory = WORK / 'runtime-locale' / locale
        directory.mkdir(parents=True, exist_ok=True)
        (directory / 'testfile.txt').write_bytes((ROOT / 'tests/testcase14/testfile.txt').read_bytes())
        command = ['env', 'LANG=' + locale, 'LC_ALL=' + locale, 'java',
                   '-Dfile.encoding=UTF-8', '-cp', linux_path(classes), 'Compiler']
        if os.name == 'nt':
            command = ['wsl', '-d', 'Ubuntu', '--cd', linux_path(directory), '--exec', *command]
        result = subprocess.run(command, cwd=directory, capture_output=True, timeout=45)
        (directory / 'runtime-stderr.txt').write_bytes(result.stderr)
        if result.returncode:
            raise AssertionError(f'{locale}: compiler exit {result.returncode}\n'
                                 + result.stderr.decode('utf-8', errors='replace'))
        assert not (directory / 'error.txt').read_text().strip()
        if locale == 'C':
            filenames = ['llvm_ir_before.txt', 'llvm_ir_after.txt', 'mips_before.txt', 'mips_after.txt']
            selected = 'mips_after.txt'
        else:
            filenames = [f'{PREFIX}_优化{stage}{kind}代码.txt'
                         for stage in ('前', '后') for kind in ('中间', '目标')]
            selected = f'{PREFIX}_优化后目标代码.txt'
        for name in filenames:
            assert (directory / name).read_text(encoding='utf-8').strip(), name
        assert (directory / 'mips.txt').read_bytes() == (directory / selected).read_bytes()
        actual, statistics = simulate(directory, b'3\n1\n2\n3\n2\n4\n5\n')
        assert actual == b'4: 4 13 22 15', actual
        print(f'{locale}: compiler and MARS passed, FinalCycle={statistics["finalCycles"]}', flush=True)


if __name__ == '__main__':
    if os.name == 'nt':
        sys.stdout.reconfigure(encoding='utf-8', errors='backslashreplace')
        sys.stderr.reconfigure(encoding='utf-8', errors='backslashreplace')
    parser = argparse.ArgumentParser()
    parser.add_argument('--classes', type=Path, default=WORK / 'classes')
    verify(parser.parse_args().classes)
