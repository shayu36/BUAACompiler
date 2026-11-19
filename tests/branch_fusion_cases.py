"""Independent predicates exercise branch fusion, including signed boundaries."""
OPS = ['<', '<=', '>', '>=', '==', '!=']
def predicate(a, op, b):
    return {'<': a < b, '<=': a <= b, '>': a > b, '>=': a >= b,
            '==': a == b, '!=': a != b}[op]

PAIRS = [(-2147483648, 2147483647), (2147483647, -2147483648),
         (-2147483648, -2147483648), (2147483647, 2147483647),
         (-1, 0), (0, -1), (0, 0), (7, 7), (7, 8), (8, 7),
         (-32768, -32767), (32767, 32768), (32768, 32767)]
statements = ''.join(f'if(a{op}b)s=s+{1 << i};' for i, op in enumerate(OPS))
pair_program = ('int main(){int n=get_int();int i=0;while(i<n){int a=get_int();'
                'int b=get_int();int s=0;' + statements +
                'printf("%d ",s);i=i+1;}return 0;}')

CONSTANTS = [-2147483648, -32769, -32768, -1, 0, 1, 32766, 32767, 32768, 2147483647]
literal = lambda n: '(-2147483647-1)' if n == -2147483648 else f'({n})'
constant_tests = [(op, c, reverse) for c in CONSTANTS for op in OPS[:4] for reverse in [False, True]]
constant_statements = ''.join(
    f'if({literal(c) if reverse else "a"}{op}{"a" if reverse else literal(c)})s=s+{i+1};'
    for i,(op,c,reverse) in enumerate(constant_tests))
constant_program = ('int main(){int n=get_int();int i=0;while(i<n){int a=get_int();int s=0;'
                    + constant_statements + 'printf("%d ",s);i=i+1;}return 0;}')
values = [-2147483648,-32769,-32768,-32767,-1,0,1,32766,32767,32768,2147483647]

CASES = {
    'branch_relational_loop': (
        'int main(){int n=get_int();int cut=get_int();int i=0;int s=0;'
        'while(i<=n){if(i>=cut)s=s+i;else s=s-i;if(cut>=i)s=s+3;'
        'if(i<=cut)s=s+7;i=i+1;}printf("%d",s);return 0;}',
        '1000\n317\n', str(sum((i if i>=317 else -i)+(3 if 317>=i else 0)+(7 if i<=317 else 0) for i in range(1001)))),
    'branch_signed_pairs': (pair_program, str(len(PAIRS))+'\n'+''.join(f'{a}\n{b}\n' for a,b in PAIRS),
                            ' '.join(str(sum(1<<i for i,op in enumerate(OPS) if predicate(a,op,b))) for a,b in PAIRS)),
    'branch_constant_boundaries': (constant_program,str(len(values))+'\n'+'\n'.join(map(str,values))+'\n',
        ' '.join(str(sum(i+1 for i,(op,c,rev) in enumerate(constant_tests) if predicate(c if rev else a,op,a if rev else c))) for a in values)),
    'branch_nested_control': (
        'int main(){int n=get_int();int i=0;int s=0;while(i<n){int j=0;'
        'while(j<=i){if(j>=n-3){j=j+1;continue;}if(i<=n-2)s=s+i-j;'
        'else s=s+j;if(j>3)break;j=j+1;}i=i+1;}printf("%d",s);return 0;}',
        '10\n','120'),
}
