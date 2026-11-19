# 比较分支与叶函数增量结果

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
- 从最终 ZIP 解压重编译，用普通 Compiler 入口运行 29 个用例，输出正确且汇编与已验证源码一致。
- 两项实现前各自的定向测试输出正确但性能门槛失败；实现后通过。全部 221 个本地用例中 85 个更快，其余持平，无加权周期回退。

## 同输入性能

公开 60 个用例总周期 **3,883,394.0 → 3,880,263.0，下降 0.08%**；52 个公开用例更快，其余持平。

| 本地用例 | 已通过平台基线 | 本轮版本 |
| --- | ---: | ---: |
| branch_relational_loop | 18,501.5 | 14,496.5 |
| leaf_return_loop | 183,138.5 | 179,138.5 |
| leaf_nested_calls | 3,753.5 | 3,673.5 |
| public_convolution | 313,046.5 | 313,006.5 |
| pressure | 2,632.0 | 2,632.0 |

本地性能不等同于隐藏测试点成绩。testcase9 和 testcase12 是否升级到 100%，需要本轮平台反馈确认。

## 提交

`D:\mycomplier\src\backend.zip` 和 `D:\mycomplier\submission.zip` 为同一文件，包含 137 个 Java 文件和 config.json。

SHA256：`8df36c8d01fa8786d8883f0188ffc61058b1a19b0ad1ccff805053c327d73d91`

```powershell
python D:\mycomplier\tests\verify_selected_baseline.py --work D:\mycomplier\tests\.backend-dfdace68 --baseline D:\mycomplier\tests\.accepted-dfdace68\src --label final --branches --leaf --copies --divrem --full --llvm --fallback --disable-option compiler.branchFusion=off --disable-option compiler.leafReturn=off --require-gain --gain-case leaf_return_loop --gain-metric cycles --gain-ratio 0.99
python D:\mycomplier\tests\verify_selected_compatibility.py --work D:\mycomplier\tests\.backend-dfdace68 --divrem --branches --leaf
python D:\mycomplier\tests\verify_branch_ir.py --work D:\mycomplier\tests\.backend-dfdace68
python D:\mycomplier\tests\verify_leaf_frame.py
& D:\mycomplier\pack.ps1
python D:\mycomplier\tests\verify_selected_package.py --work D:\mycomplier\tests\.backend-dfdace68 --divrem --branches --leaf
python D:\mycomplier\tests\report_branches.py
```
