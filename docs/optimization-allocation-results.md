# 常量乘法与叶函数参数分配

本轮基线是提交包 `d83bc231…`。用户反馈 testcase9 仍为 Accepted、≥80%、602,372 周期，全部指令计数与上一包相同；本次只回传 testcase9，未重新回传其余测试点。用户确认测试源码和输入是隐藏的。本轮完整源码与包固定在 `tests/.accepted-d83bc231`，反馈见 `platform-d83bc231.json`。

## 改动

1. 常量乘法：对模 2^32 意义下的 `2^k+1`、`2^k-1`、`1-2^k`，使用一条移位与一条加减代替乘法。覆盖 3、5、7、15、31 等常数及适用的负数，兼容结果寄存器与源寄存器相同的情况。余数重建中的常量乘法复用同一规则，保留保存原始被除数的 t2。每条选中路径只需两条 ALU 指令；不适用的常数保持原实现。
2. 叶函数参数：仅对完全没有 CALL/IO 的函数，尝试将前四个参数固定在 a0–a3，并将这些寄存器加入可分配集合。新方案必须保留原有寄存器驻留值、不增加保存寄存器数量、不破坏已有复制及转换的共用寄存器关系，也不能把原先按需从栈读取的参数改为入口无条件加载；不满足条件时采用原分配。后端省略参数已经位于正确寄存器中的自复制。

只修改 `backend/BackEnd.java`、`backend/ConstantArithmetic.java`、`optimize/RegisterAllocator.java`；前端、中端和其他 Java 文件与基线一致。全部既有优化继续启用。

同时使用 `-Dcompiler.smallMultiply=off -Dcompiler.leafArguments=off` 可恢复本次提交基线汇编。

## 验证

- 232 个用例 × 基线/候选/关闭本批三种模式，**696/696 次执行通过**；LLVM、活跃 Use-Def 检查通过。
- 关闭本批两个开关后，232 份汇编与基线逐字节一致。每个用例分别保存 verification.json，构建时记录源文件哈希，最终统一检查。
- 新增 970 组 i32 常量乘积和 36 组 i8 乘积边界组合，另包含既有 945 组除余、855 组乘法边界/随机组合。
- 覆盖指针、字符参数、八参数调用、多参数零次循环及嵌套调用；另测 80KB 栈帧与六参数，输出正确，周期 374 → 338。
- 44 次关闭全部优化的执行正确、22 份基线汇编一致；91 次延迟槽执行通过；4 个极端循环保留不终止行为。
- 最终 ZIP 解压重编译，以普通 Compiler 入口完成 40 个用例，输出正确且汇编与验证过的源码一致。

## 同输入性能

公开 60 个用例总周期 **3,880,170.0 → 3,878,427.0，下降 0.045%**；20 个更快，其余持平。全部 232 个本地用例中 95 个更快，未发现周期回退。

| 本地用例 | 已提交基线 | 本轮版本 |
| --- | ---: | ---: |
| small_multiply_loop | 44,202.0 | 39,202.0 |
| small_multiply_negative | 23,202.0 | 21,202.0 |
| leaf_argument_loop | 157,968.0 | 154,968.0 |
| leaf_argument_stack | 468.5 | 458.5 |
| leaf_argument_pointer | 509.0 | 477.0 |
| leaf_argument_zero_trip | 234.0 | 234.0 |
| public_convolution | 312,961.5 | 312,889.5 |
| public_A_testcase19 | 587,008.5 | 586,523.5 |

隐藏 testcase9 是否受益、是否达到满分，需要平台再次验证，不能由本地专项降幅推定。

## 提交与复现

`D:\mycomplier\src\backend.zip` 与 `D:\mycomplier\submission.zip` 内容一致，含 138 个 Java 文件和 config.json。

SHA256：`68690bce9106f7f70655435eb855c31b486f6bef404973093543bfcda2a513a2`

```powershell
python D:\mycomplier\tests\verify_selected_baseline.py --work D:\mycomplier\tests\.allocation-d83bc231 --baseline D:\mycomplier\tests\.accepted-d83bc231\src --label final --branches --leaf --copies --divrem --peephole --small-multiply --leaf-arguments --full --llvm --fallback --disable-option compiler.smallMultiply=off --disable-option compiler.leafArguments=off --require-gain --gain-case leaf_argument_loop --gain-metric cycles --gain-ratio 0.99
python D:\mycomplier\tests\verify_selected_compatibility.py --work D:\mycomplier\tests\.allocation-d83bc231 --divrem --branches --leaf --peephole --small-multiply --leaf-arguments
python D:\mycomplier\tests\verify_allocation_frame.py
& D:\mycomplier\pack.ps1
python D:\mycomplier\tests\verify_selected_package.py --work D:\mycomplier\tests\.allocation-d83bc231 --divrem --branches --leaf --peephole --small-multiply --leaf-arguments
python D:\mycomplier\tests\report_allocation_round.py
```
