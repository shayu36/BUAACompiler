"""Release checks against the exact submitted d83bc231 baseline."""
import hashlib
import json
from verify_phase1 import ROOT
from verify_selected_baseline import hashes
from small_multiply_cases import VALUES, FACTORS, byte_values, byte_factors

work=ROOT/'tests/.allocation-d83bc231'
baseline=ROOT/'tests/.accepted-d83bc231/src'
load=lambda path:json.loads(path.read_text(encoding='utf-8'))
run=load(work/'final/results.json')
manifest=load(work/'final/source-manifest.json')
compat=load(work/'compatibility/results.json')
large_frame=load(work/'large-frame/results.json')
package=load(work/'package/verification.json')
assert hashes(ROOT/'src')==run['source_hashes']==manifest['source_hashes']
old=hashes(baseline)
assert old==manifest['baseline_hashes']
changed=[n for n,h in run['source_hashes'].items() if old.get(n)!=h]
assert changed==['backend/BackEnd.java','backend/ConstantArithmetic.java','optimize/RegisterAllocator.java']
assert set(old)==set(run['source_hashes'])
assert all(r['passed'] for r in run['records']+large_frame) and compat['passed']
assert all(r['passed'] and r['assembly_identical'] for r in package['records'])
assert hashlib.sha256((ROOT/'src/backend.zip').read_bytes()).hexdigest()==package['sha256']
assert (ROOT/'submission.zip').read_bytes()==(ROOT/'src/backend.zip').read_bytes()
pairs={}
for r in run['records']:pairs.setdefault(r['case'],{})[r['version']]=r
assert len(pairs)==232 and len(run['records'])==696 and len(package['records'])==40
assert all(p['candidate']['cycles']<=p['baseline']['cycles'] for p in pairs.values())
assert all(p['fallback']['assembly_sha256']==p['baseline']['assembly_sha256'] for p in pairs.values())
public={n:p for n,p in pairs.items() if n.startswith('public_') and n!='public_convolution'}
assert len(public)==60
totals={v:sum(p[v]['cycles'] for p in public.values()) for v in ['baseline','candidate']}
gain=100*(1-totals['candidate']/totals['baseline'])
improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in pairs.values())
public_improved=sum(p['candidate']['cycles']<p['baseline']['cycles'] for p in public.values())
report=dict(stage='multiply_and_leaf_arguments_after_d83bc231',accepted_baseline=str(baseline),production_changes=changed,
            public_totals=totals,public_reduction_percent=gain,public_improved=public_improved,
            improved_cases=improved,regressions=0,compatibility=compat,large_frame=large_frame,package=package,**run)
(ROOT/'docs/optimization-allocation-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
release=dict(archive=str(ROOT/'src/backend.zip'),mirror=str(ROOT/'submission.zip'),sha256=package['sha256'],
             baseline=str(baseline),baseline_platform_report='platform-d83bc231.json',source_hashes=run['source_hashes'],
             verified=True,local_executions=len(run['records']),platform_results_pending=True)
(ROOT/'docs/current-submission.json').write_text(json.dumps(release,ensure_ascii=False,indent=2),encoding='utf-8')
examples=['small_multiply_loop','small_multiply_negative','leaf_argument_loop','leaf_argument_stack','leaf_argument_pointer','leaf_argument_zero_trip','public_convolution','public_A_testcase19']
rows='\n'.join(f'| {n} | {pairs[n]["baseline"]["cycles"]:,.1f} | {pairs[n]["candidate"]["cycles"]:,.1f} |' for n in examples)
text=f'''# 常量乘法与叶函数参数分配

本轮基线是提交包 `d83bc231…`。用户反馈 testcase9 仍为 Accepted、≥80%、602,372 周期，全部指令计数与上一包相同；本次只回传 testcase9，未重新回传其余测试点。用户确认测试源码和输入是隐藏的。本轮完整源码与包固定在 `tests/.accepted-d83bc231`，反馈见 `platform-d83bc231.json`。

## 改动

1. 常量乘法：对模 2^32 意义下的 `2^k+1`、`2^k-1`、`1-2^k`，使用一条移位与一条加减代替乘法。覆盖 3、5、7、15、31 等常数及适用的负数，兼容结果寄存器与源寄存器相同的情况。余数重建中的常量乘法复用同一规则，保留保存原始被除数的 t2。每条选中路径只需两条 ALU 指令；不适用的常数保持原实现。
2. 叶函数参数：仅对完全没有 CALL/IO 的函数，尝试将前四个参数固定在 a0–a3，并将这些寄存器加入可分配集合。新方案必须保留原有寄存器驻留值、不增加保存寄存器数量、不破坏已有复制及转换的共用寄存器关系，也不能把原先按需从栈读取的参数改为入口无条件加载；不满足条件时采用原分配。后端省略参数已经位于正确寄存器中的自复制。

只修改 `backend/BackEnd.java`、`backend/ConstantArithmetic.java`、`optimize/RegisterAllocator.java`；前端、中端和其他 Java 文件与基线一致。全部既有优化继续启用。

同时使用 `-Dcompiler.smallMultiply=off -Dcompiler.leafArguments=off` 可恢复本次提交基线汇编。

## 验证

- 232 个用例 × 基线/候选/关闭本批三种模式，**696/696 次执行通过**；LLVM、活跃 Use-Def 检查通过。
- 关闭本批两个开关后，232 份汇编与基线逐字节一致。每个用例分别保存 verification.json，构建时记录源文件哈希，最终统一检查。
- 新增 {len(VALUES)*len(FACTORS)} 组 i32 常量乘积和 {len(byte_values)*len(byte_factors)} 组 i8 乘积边界组合，另包含既有 945 组除余、855 组乘法边界/随机组合。
- 覆盖指针、字符参数、八参数调用、多参数零次循环及嵌套调用；另测 80KB 栈帧与六参数，输出正确，周期 374 → {large_frame[1]['cycles']:g}。
- 44 次关闭全部优化的执行正确、22 份基线汇编一致；91 次延迟槽执行通过；4 个极端循环保留不终止行为。
- 最终 ZIP 解压重编译，以普通 Compiler 入口完成 40 个用例，输出正确且汇编与验证过的源码一致。

## 同输入性能

公开 60 个用例总周期 **{totals['baseline']:,.1f} → {totals['candidate']:,.1f}，下降 {gain:.3f}%**；{public_improved} 个更快，其余持平。全部 232 个本地用例中 {improved} 个更快，未发现周期回退。

| 本地用例 | 已提交基线 | 本轮版本 |
| --- | ---: | ---: |
{rows}

隐藏 testcase9 是否受益、是否达到满分，需要平台再次验证，不能由本地专项降幅推定。

## 提交与复现

`D:\\mycomplier\\src\\backend.zip` 与 `D:\\mycomplier\\submission.zip` 内容一致，含 {package['source_files']} 个 Java 文件和 config.json。

SHA256：`{package['sha256']}`

```powershell
python D:\\mycomplier\\tests\\verify_selected_baseline.py --work D:\\mycomplier\\tests\\.allocation-d83bc231 --baseline D:\\mycomplier\\tests\\.accepted-d83bc231\\src --label final --branches --leaf --copies --divrem --peephole --small-multiply --leaf-arguments --full --llvm --fallback --disable-option compiler.smallMultiply=off --disable-option compiler.leafArguments=off --require-gain --gain-case leaf_argument_loop --gain-metric cycles --gain-ratio 0.99
python D:\\mycomplier\\tests\\verify_selected_compatibility.py --work D:\\mycomplier\\tests\\.allocation-d83bc231 --divrem --branches --leaf --peephole --small-multiply --leaf-arguments
python D:\\mycomplier\\tests\\verify_allocation_frame.py
& D:\\mycomplier\\pack.ps1
python D:\\mycomplier\\tests\\verify_selected_package.py --work D:\\mycomplier\\tests\\.allocation-d83bc231 --divrem --branches --leaf --peephole --small-multiply --leaf-arguments
python D:\\mycomplier\\tests\\report_allocation_round.py
```
'''
(ROOT/'docs/optimization-allocation-results.md').write_text(text,encoding='utf-8')
(ROOT/'src/optimize/README.md').write_text('''# 当前优化基线

当前以 d83bc231 包为基线继续。新增常量乘法两指令分解，以及无 CALL/IO 叶函数的 a0–a3 参数寄存器分配。仅修改 backend/BackEnd.java、backend/ConstantArithmetic.java、optimize/RegisterAllocator.java。

同时设置 -Dcompiler.smallMultiply=off -Dcompiler.leafArguments=off 恢复本次基线汇编。新分配若增加溢出、保存寄存器、原本已消除的复制或入口栈参数加载，则采用旧分配。

既有循环、函数、递归、访存、除余、分支及窥孔优化继续使用。前端、中端保持不变。

最新报告：D:/mycomplier/docs/optimization-allocation-results.md
最新提交身份：D:/mycomplier/docs/current-submission.json
入口：Compiler → IOhandler.PrintOptimizedOutputs。
''',encoding='utf-8')
print(json.dumps(dict(public_cycles=totals,reduction_percent=gain,improved_cases=improved,
                      regressions=0,archive_sha256=package['sha256']),indent=2))
