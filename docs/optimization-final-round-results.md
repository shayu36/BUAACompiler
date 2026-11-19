# 最后一轮增量优化：后端局部值复用

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
- 最终 ZIP 解压重编译，以普通 Compiler 入口运行 32 个用例，输出正确且汇编与已验证源码一致。
- 全部 224 个本地用例中 21 个更快，其余持平，无周期回退。

## 同输入性能

公开 60 个用例总周期 **3,880,263.0 → 3,880,170.0，下降 0.002%**；15 个更快，其余持平。

| 本地用例 | 已通过平台基线 | 本轮版本 |
| --- | ---: | ---: |
| peephole_byte_checksum | 26,948.0 | 25,948.0 |
| peephole_spill_square | 2,650.0 | 2,635.0 |
| public_A_testcase19 | 587,008.5 | 587,008.5 |
| public_convolution | 313,006.5 | 312,961.5 |
| pressure | 2,632.0 | 2,632.0 |

这些是本地同输入结果。隐藏 testcase9 是否达到满分，仍需本轮提交确认。

## 提交及复现

`D:\mycomplier\src\backend.zip` 与 `D:\mycomplier\submission.zip` 相同，包含 138 个 Java 文件和 config.json。

SHA256：`d83bc231665b9128727105bc862237c95979b7e1483c5ea97987fe3101e88864`

```powershell
python D:\mycomplier\tests\verify_selected_baseline.py --work D:\mycomplier\tests\.finish-8df36c8d --baseline D:\mycomplier\tests\.accepted-8df36c8d\src --label final --branches --leaf --copies --divrem --peephole --full --llvm --fallback --disable-option compiler.peephole=off --require-gain --gain-case peephole_byte_checksum --gain-metric cycles --gain-ratio 0.99
python D:\mycomplier\tests\verify_selected_compatibility.py --work D:\mycomplier\tests\.finish-8df36c8d --divrem --branches --leaf --peephole
python D:\mycomplier\tests\verify_branch_ir.py --work D:\mycomplier\tests\.finish-8df36c8d
python D:\mycomplier\tests\verify_peephole_machine.py --work D:\mycomplier\tests\.finish-8df36c8d
& D:\mycomplier\pack.ps1
python D:\mycomplier\tests\verify_selected_package.py --work D:\mycomplier\tests\.finish-8df36c8d --divrem --branches --leaf --peephole
python D:\mycomplier\tests\report_final_round.py
```
