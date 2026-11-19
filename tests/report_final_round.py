"""Release the final incremental round only from verified source and ZIP evidence."""
import hashlib
import json
from verify_phase1 import ROOT
from verify_selected_baseline import hashes

work=ROOT/'tests/.finish-8df36c8d'
baseline=ROOT/'tests/.accepted-8df36c8d/src'
load=lambda path:json.loads(path.read_text(encoding='utf-8'))
run=load(work/'final/results.json')
compat=load(work/'compatibility/results.json')
typed=load(work/'branch-ir/results.json')
machine=load(work/'machine/results.json')
package=load(work/'package/verification.json')
assert hashes(ROOT/'src')==run['source_hashes']
old=hashes(baseline)
changed=[n for n,h in run['source_hashes'].items() if old.get(n)!=h]
assert changed==['backend/BackEnd.java','backend/LocalPeephole.java']
assert set(run['source_hashes'])-set(old)=={'backend/LocalPeephole.java'} and not set(old)-set(run['source_hashes'])
assert all(r['passed'] for r in run['records']+typed+machine) and compat['passed']
assert all(r['passed'] and r['assembly_identical'] for r in package['records'])
assert hashlib.sha256((ROOT/'src/backend.zip').read_bytes()).hexdigest()==package['sha256']
assert (ROOT/'submission.zip').read_bytes()==(ROOT/'src/backend.zip').read_bytes()
pairs={}
for r in run['records']:pairs.setdefault(r['case'],{})[r['version']]=r
assert len(pairs)==224 and len(run['records'])==672 and len(typed)==196 and len(machine)==80
assert all(p['candidate']['cycles']<=p['baseline']['cycles'] for p in pairs.values())
assert all(p['fallback']['assembly_sha256']==p['baseline']['assembly_sha256'] for p in pairs.values())
public={n:p for n,p in pairs.items() if n.startswith('public_') and n!='public_convolution'}
assert len(public)==60
totals={v:sum(p[v]['cycles'] for p in public.values()) for v in ['baseline','candidate']}
gain=100*(1-totals['candidate']/totals['baseline'])
improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in pairs.values())
public_improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in public.values())
report=dict(stage='final_round_after_8df36c8d',accepted_baseline=str(baseline),production_changes=changed,
            public_totals=totals,public_reduction_percent=gain,public_improved=public_improved,
            improved_cases=improved,regressions=0,compatibility=compat,typed_ir=typed,machine=machine,package=package,**run)
(ROOT/'docs/optimization-final-round-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
release=dict(archive=str(ROOT/'src/backend.zip'),mirror=str(ROOT/'submission.zip'),sha256=package['sha256'],
             baseline=str(baseline),baseline_platform_report='platform-8df36c8d.json',source_hashes=run['source_hashes'],
             verified=True,local_executions=len(run['records']),platform_results_pending=True)
(ROOT/'docs/current-submission.json').write_text(json.dumps(release,ensure_ascii=False,indent=2),encoding='utf-8')
examples=['peephole_byte_checksum','peephole_spill_square','public_A_testcase19','public_convolution','pressure']
rows='\n'.join(f'| {n} | {pairs[n]["baseline"]["cycles"]:,.1f} | {pairs[n]["candidate"]["cycles"]:,.1f} |' for n in examples)
text=f'''# 最后一轮增量优化：后端局部值复用

基线为 `8df36c8d…`，平台结果 15/15 Accepted、14 项 100%，仅 testcase9 为 ≥80%（602,372 周期）。该包和源码已保存到 `tests/.accepted-8df36c8d`，用户原始反馈及加权周期核对见 `platform-8df36c8d.json`。

## 实现

新增 `backend/LocalPeephole.java`，仅在 `BackEnd` 增加一次调用；前端、中端及全部 optimize Java 文件与本次 Accepted 基线一致。

在同一段顺序执行的 MIPS 指令中跟踪寄存器值身份及 sp/gp 相对字存储值：重复 lw 在值仍存在时改为 move 或删除；相同值的重复 sw、li、move 可删除。只对普通栈/全局相对地址执行访存复用。任何写入都清除旧内存记录，再记录可识别的本次字写入；字节写入、未知指令、控制流边界、调用和 syscall 清除记录；sp/gp 改变时清除地址事实。寄存器被覆盖后不再用它转发旧值。

该变换在短分支修整之前执行，保留延迟槽 nop。`-Dcompiler.peephole=off` 可单独关闭本批，恢复已通过平台基线的汇编。

同时记录 lbu、lhu、比较和 andi 结果的无符号有效位数；只有掩码保留了所有可能为 1 的位时，才省略重复 andi。lb/lh 的符号扩展、需要保留的整数截断及非连续位掩码不会按字节零扩展处理。

## 验证

- 224 个用例 × 基线/候选/关闭本批三种模式，**672/672 次执行通过**，包括 LLVM 及活跃 Use-Def 检查。
- 关闭本批后，224 份汇编与基线逐字节一致。
- 44 次完全关闭优化的执行通过、22 份基线汇编一致；83 次延迟槽执行通过；4 个极端循环保留不终止行为。
- 196 次直接 IR 执行通过，覆盖有符号/无符号、i32/i8/i1、共享比较结果、非相邻比较和超范围分支。
- 80 次直接机器代码执行通过，覆盖源寄存器覆盖、指针别名、字节写入、sp/gp 改变、分支、调用、syscall、零值、大常量、HI/LO、符号扩展、连续与非连续掩码；普通和延迟槽模式均验证。
- 最终 ZIP 解压重编译，以普通 Compiler 入口运行 {len(package['records'])} 个用例，输出正确且汇编与已验证源码一致。
- 全部 224 个本地用例中 {improved} 个更快，其余持平，无周期回退。

## 同输入性能

公开 60 个用例总周期 **{totals['baseline']:,.1f} → {totals['candidate']:,.1f}，下降 {gain:.3f}%**；{public_improved} 个更快，其余持平。

| 本地用例 | 已通过平台基线 | 本轮版本 |
| --- | ---: | ---: |
{rows}

这些是本地同输入结果。隐藏 testcase9 是否达到满分，仍需本轮提交确认。

## 提交及复现

`D:\\mycomplier\\src\\backend.zip` 与 `D:\\mycomplier\\submission.zip` 相同，包含 {package['source_files']} 个 Java 文件和 config.json。

SHA256：`{package['sha256']}`

```powershell
python D:\\mycomplier\\tests\\verify_selected_baseline.py --work D:\\mycomplier\\tests\\.finish-8df36c8d --baseline D:\\mycomplier\\tests\\.accepted-8df36c8d\\src --label final --branches --leaf --copies --divrem --peephole --full --llvm --fallback --disable-option compiler.peephole=off --require-gain --gain-case peephole_byte_checksum --gain-metric cycles --gain-ratio 0.99
python D:\\mycomplier\\tests\\verify_selected_compatibility.py --work D:\\mycomplier\\tests\\.finish-8df36c8d --divrem --branches --leaf --peephole
python D:\\mycomplier\\tests\\verify_branch_ir.py --work D:\\mycomplier\\tests\\.finish-8df36c8d
python D:\\mycomplier\\tests\\verify_peephole_machine.py --work D:\\mycomplier\\tests\\.finish-8df36c8d
& D:\\mycomplier\\pack.ps1
python D:\\mycomplier\\tests\\verify_selected_package.py --work D:\\mycomplier\\tests\\.finish-8df36c8d --divrem --branches --leaf --peephole
python D:\\mycomplier\\tests\\report_final_round.py
```
'''
(ROOT/'docs/optimization-final-round-results.md').write_text(text,encoding='utf-8')
(ROOT/'src/optimize/README.md').write_text('''# 当前优化基线

当前以 8df36c8d 包（15/15 Accepted、14 项 100%）为基线继续。最新一批新增 backend/LocalPeephole.java，在 BackEnd 中接入局部寄存器值复用及冗余访存、常量装载、复制、已知范围内重复掩码的删除。-Dcompiler.peephole=off 可关闭本批，恢复该 Accepted 基线汇编。

已有比较分支融合、叶函数返回地址优化、MemoryForwarding、DivisionRemainders、精确 i32 复制寄存器合并及循环/函数优化继续使用。前端、中端保持不变。

入口：Compiler → IOhandler.PrintOptimizedOutputs。-Dcompiler.optimize=off 选择未优化目标；-Dcompiler.mips.delaySlots=true 保留延迟槽兼容模式。

最新报告：D:/mycomplier/docs/optimization-final-round-results.md
最新提交身份：D:/mycomplier/docs/current-submission.json
历史基础版重写的 phase1/phase2/phase3 报告不代表当前源码。
''',encoding='utf-8')
print(json.dumps(dict(public_cycles=totals,reduction_percent=gain,improved_cases=improved,
                      regressions=0,archive_sha256=package['sha256']),indent=2))
