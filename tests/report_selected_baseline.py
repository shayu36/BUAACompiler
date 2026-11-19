"""Record the selected high-score baseline and the verified, actual submission ZIP."""
import hashlib
import json
from pathlib import Path
from verify_phase1 import ROOT
from verify_selected_baseline import WORK, OLD, hashes

run=json.loads((WORK/'final/results.json').read_text(encoding='utf-8'))
compat=json.loads((WORK/'compatibility/results.json').read_text())
package=json.loads((WORK/'package/verification.json').read_text(encoding='utf-8'))
assert hashes(ROOT/'src')==run['source_hashes']
assert all(r['passed'] for r in run['records']) and compat['passed']
assert all(r['passed'] and r['assembly_identical'] for r in package['records'])
assert hashlib.sha256((ROOT/'src/backend.zip').read_bytes()).hexdigest()==package['sha256']
assert (ROOT/'src/backend.zip').read_bytes()==(ROOT/'submission.zip').read_bytes()
baseline=hashes(OLD)
changed=[n for n,h in run['source_hashes'].items() if baseline.get(n)!=h]
assert sorted(changed)==['optimize/MemoryForwarding.java','optimize/OptimizeManager.java']
assert not set(baseline)-set(run['source_hashes'])
saved=json.loads(Path('D:/RunIdea/mycomplier-next-submit-20261115/verification.json').read_text(encoding='utf-8'))['sha256']
assert all(baseline[n.replace('\\','/')]==h for n,h in saved.items())
pairs={}
for r in run['records']:pairs.setdefault(r['case'],{})[r['version']]=r
assert len(pairs)==205 and len(run['records'])==615
assert all(v['candidate']['cycles']<=v['baseline']['cycles'] for v in pairs.values())
assert all(v['fallback']['assembly_sha256']==v['baseline']['assembly_sha256'] for v in pairs.values())
public={n:v for n,v in pairs.items() if n.startswith('public_') and n!='public_convolution'}
totals={version:sum(v[version]['cycles'] for v in public.values()) for version in ['baseline','candidate']}
gain=100*(1-totals['candidate']/totals['baseline'])
report=dict(selection='User selected the mostly-100-percent complete optimized version',
            baseline_source=str(OLD),baseline_saved_manifest_matches=len(baseline),
            production_changes=changed,public_totals=totals,public_reduction_percent=gain,
            improved_cases=sum(v['candidate']['cycles']<v['baseline']['cycles'] for v in pairs.values()),
            regressions=0,compatibility=compat,package=package,**run)
(ROOT/'docs/optimization-selected-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
release=dict(archive=str(ROOT/'src/backend.zip'),mirror=str(ROOT/'submission.zip'),sha256=package['sha256'],
             baseline=str(OLD),source_hashes=run['source_hashes'],verified=True,
             local_executions=615,platform_results_pending=True)
(ROOT/'docs/current-submission.json').write_text(json.dumps(release,ensure_ascii=False,indent=2),encoding='utf-8')
examples=['memory_disjoint_elements','memory_byte','memory_call_clobber','public_convolution']
rows='\n'.join(f'| {n} | {pairs[n]["baseline"]["cycles"]:,.1f} | {pairs[n]["candidate"]["cycles"]:,.1f} |' for n in examples)
text=f'''# 从选定高分版本继续：第一批增量优化

用户指定从“大部分 100%、其余约 80%”的完整版本继续。当前工作目录仍为 `D:\\mycomplier\\src`，已恢复完整优化源码，结束基础版重写路线。

基线来源为 `{OLD}`；135 个 Java 文件全部匹配先前保存的源码 SHA256 清单。恢复包含该版本原有的编译入口、两个 IR 配套接口和输出支持；本批在此基线上仅新增 `optimize/MemoryForwarding.java`，并在 `optimize/OptimizeManager.java` 接入一行调用。前端、中端、后端和输入实现均保持选定基线的内容。

之前的第三批重写成果保存在 `{WORK / 'rewrite-src'}`。原退化 ZIP 保存在 `{WORK / 'original-archives'}`，没有丢弃。

## 本批实现

在基本块内跟踪读取和存储的内存值。相同地址可做 Store→Load 转发和 Load→Load 复用；同一数组中常量偏移不重叠的写入不再清空全部缓存。两个数组参数可能互相别名、动态下标可能覆盖已知位置、调用和输入可能修改内存时，保守清除相关记录。按类型和字节区间检查，区分 i8/i1/i32，并处理 32 位偏移回绕。

同时消除保持指针类型不变的零偏移 GEP，防止复用读取时保留了更贵的地址计算。每块缓存最多 128 项，不进行跨基本块内存推测；使用 `-Dcompiler.memoryForward=off` 可关闭新增优化。

## 验证与性能

- 205 个用例分别运行完整基线、增量版和关闭新增优化版：**615/615 次执行通过**，均检查 LLVM IR、活跃指令归属与 Use-Def 关系。
- 关闭新增优化的 205 份汇编与完整基线逐字节一致；增量版 49 个用例更快，其余持平，**本地性能回退为 0**。
- 完全关闭优化：44 次执行通过，22 份汇编与基线一致。
- 启用延迟槽：64 次执行通过；另有 4 次极限包含端点循环保持不终止行为的检查。
- 最终 ZIP 解压重编译，通过普通 `java Compiler` 入口运行 13 个定向用例，输出正确，汇编与已验证源码逐字节一致。

公开 60 个用例总周期：**{totals['baseline']:,.1f} → {totals['candidate']:,.1f}，降低 {gain:.2f}%**。其中 32 个公开用例更快，28 个持平。

| 本地用例 | 选定完整基线 | 本批增量版 |
| --- | ---: | ---: |
{rows}

数组不同元素用例的内存指令从 4,017 条降至 2,015 条。上述均为本地同输入对比，不代表平台隐藏 15 个用例的新成绩。

## 本次提交包

**可提交 `D:\\mycomplier\\src\\backend.zip` 检查平台结果。** 根目录 `submission.zip` 与它完全一致。包中有 136 个 Java 文件和 config.json，共 137 项；不包含其他 ZIP、class、测试或历史报告。

SHA256：`{package['sha256']}`

`pack.ps1` 已修正为仅打包源码和配置，并同步更新两个提交路径，避免再次把 src 中的旧 ZIP 嵌入新包或遗留未更新的 backend.zip。当前提交身份记录在 `docs/current-submission.json`。

复现命令：

```powershell
python D:\\mycomplier\\tests\\verify_selected_baseline.py --label final --full --llvm --fallback --require-gain
python D:\\mycomplier\\tests\\verify_selected_compatibility.py
& D:\\mycomplier\\pack.ps1
python D:\\mycomplier\\tests\\verify_selected_package.py
python D:\\mycomplier\\tests\\report_selected_baseline.py
```

后续继续在本基线上按平台反馈调整；保留完整循环、函数、寄存器和指令优化，不再从基础 MIPS 版本重新起步。
'''
(ROOT/'docs/optimization-selected-results.md').write_text(text,encoding='utf-8')
(ROOT/'src/optimize/README.md').write_text('''# 当前优化基线

当前已按用户要求切回“大部分 100%、其余约 80%”阶段的完整优化源码，并在其上增量改进。完整基线保留 OptimizeManager、AffineLoops、ControlFlowInline、SmallFunctionInline、ConstantCallEvaluation、LoopInvariantCalls、TailRecursion、RegisterAllocator 等实现。

本批新增 MemoryForwarding：块内 Store→Load 转发、Load→Load 复用、常量偏移别名判断和保持类型的零偏移 GEP 消除；入口是 OptimizeManager.simplifyFunctions。未知别名和调用会清除内存事实，不假定不同数组参数互不重叠。使用 -Dcompiler.memoryForward=off 可关闭本批变换，生成汇编与选定基线一致。

编译入口沿用该基线的 Compiler → IOhandler.PrintOptimizedOutputs；java -Dcompiler.optimize=off Compiler 选择未优化输出，java -Dcompiler.mips.delaySlots=true Compiler 保留延迟槽兼容模式。

当前报告：D:/mycomplier/docs/optimization-selected-results.md
提交包身份：D:/mycomplier/docs/current-submission.json
验证脚本：D:/mycomplier/tests/verify_selected_baseline.py

之前的基础版重写仅作为历史记录保存于 tests/.highscore-resume-20261118/rewrite-src，不是当前源码。不要用 phase1/phase2/phase3 重写报告判断当前版本。
''',encoding='utf-8')
for name in ['optimization-phase3-results.md','optimization-restart-plan.md']:
    path=ROOT/'docs'/name
    content=path.read_text(encoding='utf-8')
    marker='> 历史记录：已按用户最新指示切回完整高分基线。本文描述的重写版不再是当前 src；当前版本见 optimization-selected-results.md。\n\n'
    if not content.startswith(marker):path.write_text(marker+content,encoding='utf-8')
print(json.dumps(dict(public_reduction_percent=gain,public_totals=totals,production_changes=changed,archive_sha256=package['sha256'],regressions=0),ensure_ascii=False,indent=2))
