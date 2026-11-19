# 从选定高分版本继续：第一批增量优化

用户指定从“大部分 100%、其余约 80%”的完整版本继续。当前工作目录仍为 `D:\mycomplier\src`，已恢复完整优化源码，结束基础版重写路线。

基线来源为 `D:\RunIdea\mycomplier-optimized-backup-20261118\src`；135 个 Java 文件全部匹配先前保存的源码 SHA256 清单。恢复包含该版本原有的编译入口、两个 IR 配套接口和输出支持；本批在此基线上仅新增 `optimize/MemoryForwarding.java`，并在 `optimize/OptimizeManager.java` 接入一行调用。前端、中端、后端和输入实现均保持选定基线的内容。

之前的第三批重写成果保存在 `D:\mycomplier\tests\.highscore-resume-20261118\rewrite-src`。原退化 ZIP 保存在 `D:\mycomplier\tests\.highscore-resume-20261118\original-archives`，没有丢弃。

## 本批实现

在基本块内跟踪读取和存储的内存值。相同地址可做 Store→Load 转发和 Load→Load 复用；同一数组中常量偏移不重叠的写入不再清空全部缓存。两个数组参数可能互相别名、动态下标可能覆盖已知位置、调用和输入可能修改内存时，保守清除相关记录。按类型和字节区间检查，区分 i8/i1/i32，并处理 32 位偏移回绕。

同时消除保持指针类型不变的零偏移 GEP，防止复用读取时保留了更贵的地址计算。每块缓存最多 128 项，不进行跨基本块内存推测；使用 `-Dcompiler.memoryForward=off` 可关闭新增优化。

## 验证与性能

- 205 个用例分别运行完整基线、增量版和关闭新增优化版：**615/615 次执行通过**，均检查 LLVM IR、活跃指令归属与 Use-Def 关系。
- 关闭新增优化的 205 份汇编与完整基线逐字节一致；增量版 49 个用例更快，其余持平，**本地性能回退为 0**。
- 完全关闭优化：44 次执行通过，22 份汇编与基线一致。
- 启用延迟槽：64 次执行通过；另有 4 次极限包含端点循环保持不终止行为的检查。
- 最终 ZIP 解压重编译，通过普通 `java Compiler` 入口运行 13 个定向用例，输出正确，汇编与已验证源码逐字节一致。

公开 60 个用例总周期：**4,290,764.5 → 4,178,263.0，降低 2.62%**。其中 32 个公开用例更快，28 个持平。

| 本地用例 | 选定完整基线 | 本批增量版 |
| --- | ---: | ---: |
| memory_disjoint_elements | 22,645.0 | 15,641.0 |
| memory_byte | 604.0 | 444.0 |
| memory_call_clobber | 148.0 | 124.0 |
| public_convolution | 326,062.5 | 325,417.5 |

数组不同元素用例的内存指令从 4,017 条降至 2,015 条。上述均为本地同输入对比，不代表平台隐藏 15 个用例的新成绩。

## 本次提交包

**可提交 `D:\mycomplier\src\backend.zip` 检查平台结果。** 根目录 `submission.zip` 与它完全一致。包中有 136 个 Java 文件和 config.json，共 137 项；不包含其他 ZIP、class、测试或历史报告。

SHA256：`b4616102b468f174321367371013324526bf04190f13b113a04b87322f2fcbea`

`pack.ps1` 已修正为仅打包源码和配置，并同步更新两个提交路径，避免再次把 src 中的旧 ZIP 嵌入新包或遗留未更新的 backend.zip。当前提交身份记录在 `docs/current-submission.json`。

复现命令：

```powershell
python D:\mycomplier\tests\verify_selected_baseline.py --label final --full --llvm --fallback --require-gain
python D:\mycomplier\tests\verify_selected_compatibility.py
& D:\mycomplier\pack.ps1
python D:\mycomplier\tests\verify_selected_package.py
python D:\mycomplier\tests\report_selected_baseline.py
```

后续继续在本基线上按平台反馈调整；保留完整循环、函数、寄存器和指令优化，不再从基础 MIPS 版本重新起步。
