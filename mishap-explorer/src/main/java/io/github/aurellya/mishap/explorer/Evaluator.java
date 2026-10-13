package io.github.aurellya.mishap.explorer;

import io.github.aurellya.mishap.model.Expr;

import java.util.Objects;

final class Evaluator {
    private Evaluator() {}

    /** ctx = service whose local variables a bare name may refer to (null in properties). */
    static Object eval(Expr e, State s, String ctx) {
        return switch (e) {
            case Expr.IntLit i -> i.value();
            case Expr.Name n -> {
                Integer v = ctx == null ? null : s.vars().get(ctx + "." + n.name());
                yield v != null ? (Object) v : n.name();   // otherwise it is a state name
            }
            case Expr.Ref r -> r.member().equals("state")
                    ? (Object) s.loc().get(r.service())
                    : (Object) s.vars().get(r.service() + "." + r.member());
            case Expr.Not n -> !bool(eval(n.operand(), s, ctx));
            case Expr.Bin b -> bin(b, s, ctx);
        };
    }

    static boolean holds(Expr e, State s) {
        return bool(eval(e, s, null));
    }

    static int num(Object o) { return (Integer) o; }
    static boolean bool(Object o) { return (Boolean) o; }

    private static Object bin(Expr.Bin b, State s, String ctx) {
        if (b.op().equals("and")) return bool(eval(b.left(), s, ctx)) && bool(eval(b.right(), s, ctx));
        if (b.op().equals("or"))  return bool(eval(b.left(), s, ctx)) || bool(eval(b.right(), s, ctx));
        Object l = eval(b.left(), s, ctx);
        Object r = eval(b.right(), s, ctx);
        return switch (b.op()) {
            case "+"  -> num(l) + num(r);
            case "-"  -> num(l) - num(r);
            case "==" -> Objects.equals(l, r);
            case "!=" -> !Objects.equals(l, r);
            case "<"  -> num(l) < num(r);
            case ">"  -> num(l) > num(r);
            case "<=" -> num(l) <= num(r);
            case ">=" -> num(l) >= num(r);
            default -> throw new IllegalStateException("unknown operator " + b.op());
        };
    }
}