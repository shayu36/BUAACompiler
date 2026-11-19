"""Publish only freshly verified source and same-input benchmark evidence."""
import hashlib
import json
from pathlib import Path
import zipfile
from verify_phase1 import ROOT
from audit_submitted_regression import CASES as AUDIT_CASES

work=ROOT/'tests/.phase3-work'
records=json.loads((work/'final/results.json').read_text(encoding='utf-8'))
metadata=json.loads((work/'final/metadata.json').read_text(encoding='utf-8'))
compat=json.loads((work/'compatibility/results.json').read_text())
typed=json.loads((work/'ir-results.json').read_text())
assert all(r['passed'] for r in records+typed) and compat['passed']
current={p.relative_to(ROOT/'src').as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'src').rglob('*.java')}
assert current==metadata['source_hashes'],'Results do not describe current source'
with zipfile.ZipFile(ROOT/'src/backend.zip') as archive:
    untouched=[n for n in current if not n.startswith(('backend/','optimize/'))]
    assert all(hashlib.sha256(archive.read(n)).hexdigest()==current[n] for n in untouched)
    assert archive.read('backend/MipsRuntime.java')==(ROOT/'src/backend/MipsRuntime.java').read_bytes()
for path in (work/'final/classes').rglob('*.class'):
    assert path.read_bytes()==(work/'compatibility-classes'/path.relative_to(work/'final/classes')).read_bytes(),path

cases={}
for r in records:cases.setdefault(r['case'],{})[r['version']]=r['cycles']
assert len(cases)==198 and len(records)==562,(len(cases),len(records))
public={k:v for k,v in cases.items() if k.startswith('public_') and k!='public_convolution'}
assert len(public)==60
totals={v:sum(c[v] for c in public.values()) for v in ['submitted','candidate','historical']}
old_regressions={k:v for k,v in cases.items() if v['candidate']>v['historical']}
submitted_regressions={k:v for k,v in cases.items() if 'submitted' in v and v['candidate']>v['submitted']}
report=dict(stage='rewrite_phase3',submission_recommended=False,
            source_scope_unchanged_files=len(untouched),runtime_source_unchanged=True,
            verification=dict(executions=len(records),cases=len(cases),llvm_verified_candidates=len(cases),
                              typed_ir_executions=len(typed),compatibility=compat),
            public_cycles=totals,old_regressions=old_regressions,submitted_regressions=submitted_regressions,
            metadata=metadata,records=records)
(ROOT/'docs/optimization-phase3-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
rows='\n'.join(f'| {name} | {cases[name]["submitted"]:,.1f} | {cases[name]["candidate"]:,.1f} | {cases[name]["historical"]:,.1f} |' for name in AUDIT_CASES)
improvement=100*(1-totals['candidate']/totals['submitted'])
old_gap=100*(totals['candidate']/totals['historical']-1)
text=f'''# 第三批重写：循环、常量运算与函数优化

本批源码位于 `D:\\mycomplier\\src`。继续当前重写，没有整体恢复旧版。生产代码只修改 optimize 和 backend；{len(untouched)} 个其他 Java 文件与原提交包逐字节一致，MipsRuntime.java 也保持一致。

## 已实现

- 固定次数仿射循环的矩阵快速幂；运行时严格正反向计数、`!=`、独立累加和最后赋值公式。保留零次循环，次数使用无符号值；三角求和先除偶数因子。固定次数循环去掉入口判断，动态循环在出口重算必要值以删除回边。
- 常量乘法移位/加减；常量除法魔数高位乘法与移位；取模按商重构。涵盖负数向零截断、余数符号、INT_MIN 和负除数。
- 有预算的单基本块内联；纯函数副作用分析；常量参数 IR 解释执行和支配约束的纯调用复用。解释执行限制 250,000 条指令、96 层递归和 8,192 个缓存项，无法确定则保留调用。
- 符合条件的纯分支递归使用 256 项运行时缓存；限 1–2 个 i32 参数，命中时核对完整参数，哈希冲突不会返回其他参数的结果。
- 只读全局标量传播、仅 main 使用的标量局部化，使后续函数/循环优化可以识别这些值；线性块合并。
- 比较与分支融合、利用顺序执行、寄存器直接寻址、ASCII 常量字符串直接输出；实测课程 MARS 关闭延迟槽后删除 nop，保留显式兼容开关。

## 验证

当前源码、完整旧版备份、原提交包共 **{len(records)}/{len(records)} 次执行输出正确**，覆盖 {len(cases)} 个不同用例，其中公开测试 60 个。原提交版不执行 32 个可能需要数十亿次迭代的边界用例；这些用例用独立公式预期值、当前实现和完整旧版共同验证。

当前 {len(cases)} 个用例均通过 LLVM IR 验证及指令归属、Use-Def 一致性检查。另有 6/6 窄整数/逃逸地址 IR 执行、106 次关闭优化后的逐字节一致性检查、106 次确定性检查、57 次启用延迟槽的执行检查，以及 4 次极限包含端点循环保留不终止行为的检查。

常量算术用例包含 945 组商/余数和 855 个乘法结果，预期值由 Python 整数运算计算。循环用例包含跨有符号边界的动态次数、正反向 `!=`、零次执行和 20 组随机仿射状态更新。

## 同输入周期对比

以下是本地定向程序，**不是平台隐藏的 testcase1–15**。完整旧版基线来自 `D:\\RunIdea\\mycomplier-optimized-backup-20261118\\src`，未证明与旧平台提交包逐字节相同。

| 本地用例 | 原提交 ZIP | 当前重写 | 完整旧版备份 |
| --- | ---: | ---: | ---: |
{rows}

公开 60 个用例总周期：原提交包 **{totals['submitted']:,.1f}** → 当前 **{totals['candidate']:,.1f}**，下降 **{improvement:.2f}%**；完整旧版为 **{totals['historical']:,.1f}**，当前仍高 **{old_gap:.2f}%**。总周期是本地运行成本汇总，不能转换成平台性能赋分。

与完整旧版相比，{len(old_regressions)}/{len(cases)} 个用例周期仍较高；与原提交包的共同用例相比，{len(submitted_regressions)} 个用例周期较高。完整逐例数据和源码 SHA256 保存在同名 JSON 中。

## 提交结论与下一步

**本批不建议提交平台。** 已补回导致数量级退化的循环和函数优化，但复杂数组程序、跨分支内联、循环不变调用、递归调用现场和复制开销仍有差距。下一批先按这些实测差距排序，避免只凭对重写上一批的提升判断可提交。

`D:\\mycomplier\\src\\backend.zip` 保留为原退化提交的审计证据，**没有覆盖成第三批源码包**。其 SHA256 为 `{metadata['submitted_archive_sha256']}`。旧版、原包与当前编译产物放在不同目录，测试也记录当前源码哈希，检测到测试中途修改源码会要求重跑。

复现：`python D:\\mycomplier\\tests\\verify_phase3.py --old --submitted --llvm --public`。兼容性检查使用 `verify_phase3_compatibility.py`；报告生成脚本为 `report_phase3.py`。
'''
(ROOT/'docs/optimization-phase3-results.md').write_text(text,encoding='utf-8')
print(json.dumps(dict(executions=len(records),public_cycles=totals,improvement_percent=improvement,old_gap_percent=old_gap,
                      old_regressions=len(old_regressions),submitted_regressions=len(submitted_regressions),audit={n:cases[n] for n in AUDIT_CASES}),ensure_ascii=False,indent=2))
