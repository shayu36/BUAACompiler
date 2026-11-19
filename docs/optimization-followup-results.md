# 已通过平台版本上的第二批增量优化

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
- 最终提交包解压重编译，通过普通 Compiler 入口运行 21 个用例，汇编与已验证源码一致。

最初全量测试拦下了两个各增加 1 周期的用例，原因是无符号 `% 2` 已有更便宜的掩码实现。加入成本限制后重新跑完全部验证，**本地性能回退为 0**。

## 同输入性能

公开 60 个用例总周期 **4,178,263.0 → 3,883,394.0，降低 7.06%**；36 个公开用例更快，其余持平。全部 213 个用例中，114 个更快，其余持平。

| 本地用例 | 上次通过平台版本 | 本轮增量版 |
| --- | ---: | ---: |
| divrem_reuse | 44,783.0 | 29,782.0 |
| divrem_negative | 45,805.0 | 30,804.0 |
| copy_loop_control | 13,697.0 | 12,696.0 |
| copy_live_old_value | 13,697.0 | 11,696.0 |
| public_convolution | 325,417.5 | 313,046.5 |
| pressure | 2,632.0 | 2,632.0 |

`divrem_reuse` 的除法次数从 2,000 次降到 1,000 次。这些是本地同输入结果，平台隐藏 testcase9、12、15 的新成绩需要本次提交确认。

## 提交与复现

可提交 **`D:\mycomplier\src\backend.zip`**。根目录 `submission.zip` 是同一份包。包含 137 个 Java 文件和 config.json；没有嵌套 ZIP 或 class 文件。

SHA256：`dfdace6870385db59600d2c81d42ecf4e15fb0ee7e1defb23ab841f6c8dc5963`

关闭本批变换：`java -Dcompiler.divRemReuse=off -Dcompiler.copyAware=off Compiler`。

```powershell
python D:\mycomplier\tests\verify_selected_baseline.py --work D:\mycomplier\tests\.followup-b4616102 --baseline D:\mycomplier\tests\.accepted-b4616102\src --label final --copies --divrem --full --llvm --fallback --disable-option compiler.divRemReuse=off --disable-option compiler.copyAware=off --require-gain --gain-case divrem_reuse --gain-metric cycles --gain-ratio 0.9
python D:\mycomplier\tests\verify_selected_compatibility.py --work D:\mycomplier\tests\.followup-b4616102 --divrem
python D:\mycomplier\tests\verify_followup_ir.py
& D:\mycomplier\pack.ps1
python D:\mycomplier\tests\verify_selected_package.py --work D:\mycomplier\tests\.followup-b4616102 --divrem
python D:\mycomplier\tests\report_followup.py
```
