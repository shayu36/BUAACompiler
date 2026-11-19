"""Generate the release identity only from completed source/package checks."""
import hashlib
import json
from verify_phase1 import ROOT
from verify_selected_baseline import hashes

work=ROOT/'tests/.backend-dfdace68'
baseline=ROOT/'tests/.accepted-dfdace68/src'
load=lambda path:json.loads(path.read_text(encoding='utf-8'))
run=load(work/'final/results.json')
compat=load(work/'compatibility/results.json')
typed=load(work/'branch-ir/results.json')
large_frame=load(work/'leaf-extra/results.json')
package=load(work/'package/verification.json')
assert hashes(ROOT/'src')==run['source_hashes']
old=hashes(baseline)
changed=[n for n,h in run['source_hashes'].items() if old.get(n)!=h]
assert changed==['backend/BackEnd.java','backend/MipsModule.java'] and set(old)==set(run['source_hashes'])
assert all(r['passed'] for r in run['records']+typed+large_frame) and compat['passed']
assert all(r['passed'] and r['assembly_identical'] for r in package['records'])
assert hashlib.sha256((ROOT/'src/backend.zip').read_bytes()).hexdigest()==package['sha256']
assert (ROOT/'submission.zip').read_bytes()==(ROOT/'src/backend.zip').read_bytes()
pairs={}
for r in run['records']:pairs.setdefault(r['case'],{})[r['version']]=r
assert len(pairs)==221 and len(run['records'])==663 and len(typed)==196
assert all(p['candidate']['cycles']<=p['baseline']['cycles'] for p in pairs.values())
assert all(p['fallback']['assembly_sha256']==p['baseline']['assembly_sha256'] for p in pairs.values())
public={n:p for n,p in pairs.items() if n.startswith('public_') and n!='public_convolution'}
assert len(public)==60
totals={v:sum(p[v]['cycles'] for p in public.values()) for v in ['baseline','candidate']}
gain=100*(1-totals['candidate']/totals['baseline'])
improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in pairs.values())
public_improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in public.values())
report=dict(stage='backend_after_dfdace68',accepted_baseline=str(baseline),production_changes=changed,
            public_totals=totals,public_reduction_percent=gain,public_improved=public_improved,
            improved_cases=improved,regressions=0,compatibility=compat,typed_ir=typed,large_frame=large_frame,package=package,**run)
(ROOT/'docs/optimization-branches-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
release=dict(archive=str(ROOT/'src/backend.zip'),mirror=str(ROOT/'submission.zip'),sha256=package['sha256'],
             baseline=str(baseline),baseline_platform_report='platform-dfdace68.json',source_hashes=run['source_hashes'],
             verified=True,local_executions=len(run['records']),platform_results_pending=True)
(ROOT/'docs/current-submission.json').write_text(json.dumps(release,ensure_ascii=False,indent=2),encoding='utf-8')
examples=['branch_relational_loop','leaf_return_loop','leaf_nested_calls','public_convolution','pressure']
rows='\n'.join(f'| {n} | {pairs[n]["baseline"]["cycles"]:,.1f} | {pairs[n]["candidate"]["cycles"]:,.1f} |' for n in examples)
text=f'''# 比较分支与叶函数增量结果

本轮基线为 `dfdace68…`，用户提供的平台成绩为 15/15 Accepted、13 项 100%，仅 testcase9 和 testcase12 为 ≥80%。该包和源码已固定在 `tests/.accepted-dfdace68`，原始反馈见 `platform-dfdace68.json`。

## 实现范围

生产代码只修改 `backend/BackEnd.java` 和 `backend/MipsModule.java`，全部 optimize、前端和中端 Java 文件与本次 Accepted 基线一致。

对紧邻条件跳转且仅被该跳转使用的比较，扩展到全部有符号及无符号关系谓词。直接根据 slt/sltu/slti 的结果选择 beq/bne，不再为 <=、>= 等比较单独执行布尔取反，也不再存取只用于跳转的布尔值。保留既有零值分支、立即数选择和远跳转保护；阈值加一使用 64 位计算，避免 INT_MAX 溢出。多处使用或与分支不相邻的比较继续物化其结果。

对实际生成的函数指令统计 jal/jalr，只在完全没有调用的叶函数中删除保存、恢复返回地址的两次访存。保持栈帧大小及传参偏移，运行库调用和递归仍保存返回地址；直接输出系统调用不写 $ra，可以使用这一优化。

同时使用 `-Dcompiler.branchFusion=off -Dcompiler.leafReturn=off` 关闭本批增量，恢复本次 Accepted 基线的汇编。其他已通过平台的优化继续启用。

## 验证证据

- 221 个用例 × 基线/候选/关闭本批三种模式，663/663 次输出与 LLVM、活跃 Use-Def 检查通过。
- 关闭本批两个开关后，221 份汇编与基线逐字节一致。
- 44 次关闭所有优化的执行通过，22 份基线汇编一致；80 次延迟槽兼容执行通过；4 个极端包含端点循环保留不终止行为。
- 196 次直接 IR 执行通过：i32/i8/i1、有符号/无符号谓词、真假分支布局、共享布尔值、非相邻比较，以及超过 32K 条指令距离的跳转；含普通与延迟槽两种模式。
- 另测 80KB 大栈帧叶函数的基线/候选/关闭本批三种模式，输出与 LLVM 检查通过，关闭开关恢复基线汇编，周期 384 → 374。
- 从最终 ZIP 解压重编译，用普通 Compiler 入口运行 {len(package['records'])} 个用例，输出正确且汇编与已验证源码一致。
- 两项实现前各自的定向测试输出正确但性能门槛失败；实现后通过。全部 221 个本地用例中 {improved} 个更快，其余持平，无加权周期回退。

## 同输入性能

公开 60 个用例总周期 **{totals['baseline']:,.1f} → {totals['candidate']:,.1f}，下降 {gain:.2f}%**；{public_improved} 个公开用例更快，其余持平。

| 本地用例 | 已通过平台基线 | 本轮版本 |
| --- | ---: | ---: |
{rows}

本地性能不等同于隐藏测试点成绩。testcase9 和 testcase12 是否升级到 100%，需要本轮平台反馈确认。

## 提交

`D:\\mycomplier\\src\\backend.zip` 和 `D:\\mycomplier\\submission.zip` 为同一文件，包含 {package['source_files']} 个 Java 文件和 config.json。

SHA256：`{package['sha256']}`

```powershell
python D:\\mycomplier\\tests\\verify_selected_baseline.py --work D:\\mycomplier\\tests\\.backend-dfdace68 --baseline D:\\mycomplier\\tests\\.accepted-dfdace68\\src --label final --branches --leaf --copies --divrem --full --llvm --fallback --disable-option compiler.branchFusion=off --disable-option compiler.leafReturn=off --require-gain --gain-case leaf_return_loop --gain-metric cycles --gain-ratio 0.99
python D:\\mycomplier\\tests\\verify_selected_compatibility.py --work D:\\mycomplier\\tests\\.backend-dfdace68 --divrem --branches --leaf
python D:\\mycomplier\\tests\\verify_branch_ir.py --work D:\\mycomplier\\tests\\.backend-dfdace68
python D:\\mycomplier\\tests\\verify_leaf_frame.py
& D:\\mycomplier\\pack.ps1
python D:\\mycomplier\\tests\\verify_selected_package.py --work D:\\mycomplier\\tests\\.backend-dfdace68 --divrem --branches --leaf
python D:\\mycomplier\\tests\\report_branches.py
```
'''
(ROOT/'docs/optimization-branches-results.md').write_text(text,encoding='utf-8')
(ROOT/'src/optimize/README.md').write_text('''# 当前优化基线

当前以 dfdace68 包（15/15 Accepted、13 项 100%）为基线继续。最新一批只修改 backend/BackEnd.java 和 MipsModule.java，扩展比较与分支融合，删除叶函数的返回地址存取；同时设置 -Dcompiler.branchFusion=off -Dcompiler.leafReturn=off 可关闭本批并恢复该基线汇编。

已有 MemoryForwarding、DivisionRemainders、精确 i32 复制寄存器合并以及循环公式、函数内联、编译期求值、尾递归和记忆化继续使用。前端与中端保持不变。

入口：Compiler → IOhandler.PrintOptimizedOutputs。-Dcompiler.optimize=off 选择未优化目标；-Dcompiler.mips.delaySlots=true 启用延迟槽兼容模式。

最新报告：D:/mycomplier/docs/optimization-branches-results.md
最新提交身份：D:/mycomplier/docs/current-submission.json
复现命令和验证证据见最新报告；phase1/phase2/phase3 属于历史基础版重写记录。
''',encoding='utf-8')
print(json.dumps(dict(public_cycles=totals,reduction_percent=gain,improved_cases=improved,
                      regressions=0,archive_sha256=package['sha256']),indent=2))
