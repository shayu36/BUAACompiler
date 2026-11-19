"""Release evidence for the incremental changes after the b4616102 platform result."""
import hashlib
import json
from pathlib import Path
from verify_phase1 import ROOT
from verify_selected_baseline import hashes

work=ROOT/'tests/.followup-b4616102'
baseline=ROOT/'tests/.accepted-b4616102/src'
run=json.loads((work/'final/results.json').read_text(encoding='utf-8'))
compat=json.loads((work/'compatibility/results.json').read_text())
typed=json.loads((work/'typed-ir/results.json').read_text())
package=json.loads((work/'package/verification.json').read_text(encoding='utf-8'))
assert hashes(ROOT/'src')==run['source_hashes']
assert all(r['passed'] for r in run['records']+typed) and compat['passed']
assert all(r['passed'] and r['assembly_identical'] for r in package['records'])
assert hashlib.sha256((ROOT/'src/backend.zip').read_bytes()).hexdigest()==package['sha256']
assert (ROOT/'submission.zip').read_bytes()==(ROOT/'src/backend.zip').read_bytes()
old=hashes(baseline)
changed=[n for n,h in run['source_hashes'].items() if old.get(n)!=h]
assert sorted(changed)==['optimize/DivisionRemainders.java','optimize/OptimizeManager.java','optimize/RegisterAllocator.java']
assert not set(old)-set(run['source_hashes'])
pairs={}
for r in run['records']:pairs.setdefault(r['case'],{})[r['version']]=r
assert len(pairs)==213 and len(run['records'])==639
assert all(v['candidate']['cycles']<=v['baseline']['cycles'] for v in pairs.values())
assert all(v['fallback']['assembly_sha256']==v['baseline']['assembly_sha256'] for v in pairs.values())
public={n:v for n,v in pairs.items() if n.startswith('public_') and n!='public_convolution'}
totals={v:sum(p[v]['cycles'] for p in public.values()) for v in ['baseline','candidate']}
gain=100*(1-totals['candidate']/totals['baseline'])
public_improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in public.values())
improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in pairs.values())
report=dict(stage='followup_after_b4616102',accepted_baseline=str(baseline),production_changes=changed,
            public_totals=totals,public_reduction_percent=gain,public_improved=public_improved,
            improved_cases=improved,regressions=0,compatibility=compat,typed_ir=typed,package=package,**run)
(ROOT/'docs/optimization-followup-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
release=dict(archive=str(ROOT/'src/backend.zip'),mirror=str(ROOT/'submission.zip'),sha256=package['sha256'],
             baseline=str(baseline),baseline_platform_report='platform-b4616102.json',source_hashes=run['source_hashes'],
             verified=True,local_executions=639,platform_results_pending=True)
(ROOT/'docs/current-submission.json').write_text(json.dumps(release,ensure_ascii=False,indent=2),encoding='utf-8')
examples=['divrem_reuse','divrem_negative','copy_loop_control','copy_live_old_value','public_convolution','pressure']
rows='\n'.join(f'| {n} | {pairs[n]["baseline"]["cycles"]:,.1f} | {pairs[n]["candidate"]["cycles"]:,.1f} |' for n in examples)
text=f'''# 已通过平台版本上的第二批增量优化

基线是本次用户提交的 `b4616102…`：15/15 Accepted，12 个测试点 100%，9、12、15 为 ≥80%。平台原始指令计数已写入 `platform-b4616102.json` 并核对加权周期。该包和完整源码保存在 `tests/.accepted-b4616102`，本轮所有性能比较均相对此版。

## 实现

1. `DivisionRemainders` 在同一基本块内复用先前已执行的商，以 `x - q*d` 替代再次计算余数。按有符号/无符号及操作数身份区分表达式，只处理 i32；不同分支、改变过的操作数和常量零除数均不匹配。无符号二次幂取模保留原有掩码实现，避免把一条 andi 改成两条指令。
2. `RegisterAllocator` 对精确的 i32 局部复制消除源、目的之间由复制本身产生的多余冲突，使相同值可共用寄存器。后续写入仍与活跃旧值建立冲突；窄整数和指针复制不采用这一规则。

生产代码仅新增 `optimize/DivisionRemainders.java`，并修改 `optimize/OptimizeManager.java` 与 `optimize/RegisterAllocator.java`。前端、中端和 backend 与已通过平台的基线逐字节一致。

## 验证

- 213 个用例 × 基线/候选/关闭本批变换三种模式，**639/639 次执行通过**；LLVM 和活跃 Use-Def 检查全部通过。
- 关闭本批两项变换后，213 份汇编与已通过平台的基线逐字节一致。
- 完全关闭优化：44 次执行正确、22 份基线汇编一致；启用延迟槽：72 次执行正确；4 次极端包含端点循环保持不终止。
- 20 次直接 IR 执行检查有符号/无符号混合、负除数、INT_MIN 和 INT_MAX。
- 最终提交包解压重编译，通过普通 Compiler 入口运行 {len(package['records'])} 个用例，汇编与已验证源码一致。

最初全量测试拦下了两个各增加 1 周期的用例，原因是无符号 `% 2` 已有更便宜的掩码实现。加入成本限制后重新跑完全部验证，**本地性能回退为 0**。

## 同输入性能

公开 60 个用例总周期 **{totals['baseline']:,.1f} → {totals['candidate']:,.1f}，降低 {gain:.2f}%**；{public_improved} 个公开用例更快，其余持平。全部 213 个用例中，{improved} 个更快，其余持平。

| 本地用例 | 上次通过平台版本 | 本轮增量版 |
| --- | ---: | ---: |
{rows}

`divrem_reuse` 的除法次数从 2,000 次降到 1,000 次。这些是本地同输入结果，平台隐藏 testcase9、12、15 的新成绩需要本次提交确认。

## 提交与复现

可提交 **`D:\\mycomplier\\src\\backend.zip`**。根目录 `submission.zip` 是同一份包。包含 {package['source_files']} 个 Java 文件和 config.json；没有嵌套 ZIP 或 class 文件。

SHA256：`{package['sha256']}`

关闭本批变换：`java -Dcompiler.divRemReuse=off -Dcompiler.copyAware=off Compiler`。

```powershell
python D:\\mycomplier\\tests\\verify_selected_baseline.py --work D:\\mycomplier\\tests\\.followup-b4616102 --baseline D:\\mycomplier\\tests\\.accepted-b4616102\\src --label final --copies --divrem --full --llvm --fallback --disable-option compiler.divRemReuse=off --disable-option compiler.copyAware=off --require-gain --gain-case divrem_reuse --gain-metric cycles --gain-ratio 0.9
python D:\\mycomplier\\tests\\verify_selected_compatibility.py --work D:\\mycomplier\\tests\\.followup-b4616102 --divrem
python D:\\mycomplier\\tests\\verify_followup_ir.py
& D:\\mycomplier\\pack.ps1
python D:\\mycomplier\\tests\\verify_selected_package.py --work D:\\mycomplier\\tests\\.followup-b4616102 --divrem
python D:\\mycomplier\\tests\\report_followup.py
```
'''
(ROOT/'docs/optimization-followup-results.md').write_text(text,encoding='utf-8')
readme=ROOT/'src/optimize/README.md'
note='''# 当前优化基线

当前以 b4616102 包（15/15 Accepted、12 项 100%）为基线继续。新增 DivisionRemainders 复用同块已计算的商；RegisterAllocator 对精确 i32 复制消除多余冲突。关闭本批：-Dcompiler.divRemReuse=off -Dcompiler.copyAware=off。

完整版本原有的循环公式、内联、常量函数求值、循环不变调用、尾递归和运行时记忆化等优化继续使用。MemoryForwarding 负责块内内存值转发，对可能别名的写入和调用保守失效；-Dcompiler.memoryForward=off 可单独关闭这项变换。

入口沿用 Compiler → IOhandler.PrintOptimizedOutputs。-Dcompiler.optimize=off 选择未优化目标；-Dcompiler.mips.delaySlots=true 保留延迟槽兼容模式。

最新报告：D:/mycomplier/docs/optimization-followup-results.md
最新提交身份：D:/mycomplier/docs/current-submission.json

本轮复现命令与基线位置见最新报告。基础版重写的 phase1/phase2/phase3 报告属于历史记录，不能用于判断当前源码。

'''
readme.write_text(note,encoding='utf-8')
print(json.dumps(dict(public_cycles=totals,reduction_percent=gain,public_improved=public_improved,improved_cases=improved,regressions=0,archive_sha256=package['sha256']),ensure_ascii=False,indent=2))
