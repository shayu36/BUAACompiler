# 当前优化基线

当前以 d83bc231 包为基线继续。新增常量乘法两指令分解，以及无 CALL/IO 叶函数的 a0–a3 参数寄存器分配。仅修改 backend/BackEnd.java、backend/ConstantArithmetic.java、optimize/RegisterAllocator.java。

同时设置 -Dcompiler.smallMultiply=off -Dcompiler.leafArguments=off 恢复本次基线汇编。新分配若增加溢出、保存寄存器、原本已消除的复制或入口栈参数加载，则采用旧分配。

既有循环、函数、递归、访存、除余、分支及窥孔优化继续使用。前端、中端保持不变。

最新报告：D:/mycomplier/docs/optimization-allocation-results.md
最新提交身份：D:/mycomplier/docs/current-submission.json
入口：Compiler → IOhandler.PrintOptimizedOutputs。
