package frontend.ast.exp.recursion;

import frontend.ast.Node;
import frontend.ast.SyntaxType;
import frontend.ast.exp.ComputeExp;

import java.util.ArrayList;

/**
 * 左递归表达式的公共基类（MulExp / AddExp / RelExp / EqExp / LAndExp / LOrExp）。
 * 解决办法分两阶段：
 * <p>
 * 【阶段一：平铺】改写成等价的非递归形式结果收进 nodeList：
 * nodeList = [MulExp, op, MulExp, op, MulExp, ...]
 * 偶数下标是操作数，奇数下标是运算符。
 * <p>
 * 【阶段二：重建】HandleRecursion把扁平列表折成左结合的二叉树
 * ((MulExp op MulExp) op MulExp) op ...
 */
public abstract class RecursionNode extends ComputeExp {
    // 阶段一收集到的扁平列表（偶数下标=操作数，奇数下标=运算符）
    protected final ArrayList<Node> nodeList;
    
    public RecursionNode(SyntaxType syntaxType) {
        super(syntaxType);
        this.nodeList = new ArrayList<>();
    }
    
    // 与 AddNode 的区别：只解析并收进 nodeList，先不挂到 components 上
    protected void AddNodeList(Node node) {
        node.Parse();
        this.nodeList.add(node);
    }
    
    /**
     * 阶段二：把 nodeList 重建成左结合二叉树。
     * <p>
     * 下标规律（n 个操作数、n-1 个运算符，size = 2n-1）：
     * 0      1      2      3      4    ...  2n-3      2n-2
     * B0     op1    B1     op2    B2    ...  op_{n-1}  B_{n-1}
     * <p>
     * 步骤：
     * ① 把第一个操作数包一层
     * ② 从左往右折叠 n-2 次，每轮吃掉一对 (运算符, 操作数)
     * ③ 最后剩下的 1 个运算符和 1 个操作数直接放顶层
     * <p>
     * 以 a + b + c 为例，nodeList = [MulExp(a), '+', MulExp(b), '+', MulExp(c)]：
     * ① exp = AddExp(MulExp(a))
     * ② exp = AddExp(exp, '+', MulExp(b))
     * ③ components = [exp, '+', MulExp(c)]
     * 得到 ((MulExp(a) + MulExp(b)) + MulExp(c))，正是左结合。
     * （MulExp(a) 表示"装着 a 的那棵 MulExp 子树"）
     *
     * @param constructor1To1 类型是 Generate1Node1One<Node, Node>，
     *                        表示"吃 1 个 Node、吐 1 个 Node"的动作。
     *                        泛型填实后等价于 Node apply(Node t)。
     *                        方法体内的调用：constructor1To1.apply(nodeList.get(0))
     *                        实际执行：new AddExp(nodeList.get(0))
     *                        对应子类的一参构造器：AddExp(Node node)
     *                        用途：步骤 ① 把第一个操作数包一层。
     *                        注意文法左边必须是 AddExp 本身，所以即使只有一个
     *                        操作数也要包，不能直接用裸的 MulExp。
     * @param constructor3To1 类型是 Generate3Node2One<Node, Node, Node, Node>，
     *                        表示"吃 3 个 Node、吐 1 个 Node"的动作。
     *                        泛型填实后等价于 Node apply(Node t, Node u, Node v)。
     *                        方法体内的调用：constructor3To1.apply(exp, node2, node3)
     *                        实际执行：new AddExp(exp, node2, node3)
     *                        对应子类的三参构造器：AddExp(Node, Node, Node)
     *                        用途：步骤 ② 每轮把 (已折好的左子树, 运算符, 操作数)
     *                        组成新节点，实现左结合。
     *                        <p>
     *                        两个参数在子类里都写作方法引用 AddExp::new——
     *                        因为目标类型不同，编译器会自动挑中对应的那个构造器：
     *                        放到第 1 个参数位置 → 选中 AddExp(Node)
     *                        放到第 2 个参数位置 → 选中 AddExp(Node, Node, Node)
     */
    protected void HandleRecursion(
        Generate1Node1One<Node, Node> constructor1To1,
        Generate3Node2One<Node, Node, Node, Node> constructor3To1) {
        Node exp = this.nodeList.get(0);
        if (this.nodeList.size() > 1) {
            int index = 1;
            // ① 包一层
            exp = constructor1To1.apply(this.nodeList.get(0));
            // ② 左折叠：每轮消费 (运算符, 操作数) 一对
            while (index < this.nodeList.size() - 2) {
                // op
                Node node2 = this.nodeList.get(index++);
                // 操作数
                Node node3 = this.nodeList.get(index++);
                exp = constructor3To1.apply(exp, node2, node3);
            }
            // ③ 折叠结果 + 最后的运算符 + 最后的操作数
            this.components.add(exp);
            this.components.add(this.nodeList.get(index++));
            this.components.add(this.nodeList.get(index));
        } else {
            // 只有一个操作数
            this.components.add(exp);
        }
    }
    
    @Override
    public void Compute() {
    }
    
    @FunctionalInterface
    interface Generate1Node1One<T, R> {
        R apply(T t);
    }
    
    @FunctionalInterface
    interface Generate3Node2One<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}
