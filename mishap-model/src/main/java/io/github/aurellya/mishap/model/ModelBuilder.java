package io.github.aurellya.mishap.model;

import io.github.aurellya.mishap.dsl.MishapBaseVisitor;
import io.github.aurellya.mishap.dsl.MishapParser;
import io.github.aurellya.mishap.dsl.MishapParser.*;

import java.util.*;

final class ModelBuilder {

    Spec build(SystemContext ctx) {
        List<MsgRef> init = new ArrayList<>();
        List<Service> services = new ArrayList<>();
        Set<String> failures = new LinkedHashSet<>();
        List<MsgRef> lossy = new ArrayList<>();
        Bounds bounds = new Bounds(20, 4, 0, 0);
        List<Property> props = new ArrayList<>();

        for (MemberContext m : ctx.member()) {
            if (m.initDecl() != null) {
                m.initDecl().sendRef().forEach(s -> init.add(msgRef(s.msgRef())));
            } else if (m.service() != null) {
                services.add(service(m.service()));
            } else if (m.failuresDecl() != null) {
                m.failuresDecl().failure().forEach(f -> failures.add(f.getText()));
            } else if (m.lossyDecl() != null) {
                m.lossyDecl().msgRef().forEach(r -> lossy.add(msgRef(r)));
            } else if (m.boundsDecl() != null) {
                bounds = bounds(m.boundsDecl());
            } else {
                props.add(property(m.property()));
            }
        }
        return new Spec(ctx.ID().getText(), init, services, failures, lossy, bounds, props);
    }

    private MsgRef msgRef(MsgRefContext c) {
        return new MsgRef(c.ID(0).getText(), c.ID(1).getText());
    }

    private Service service(ServiceContext c) {
        List<String> states = new ArrayList<>();
        String initial = null;
        Map<String, Integer> vars = new LinkedHashMap<>();
        List<Transition> transitions = new ArrayList<>();

        for (ServiceMemberContext m : c.serviceMember()) {
            if (m.stateDecl() != null) {
                m.stateDecl().ID().forEach(id -> states.add(id.getText()));
            } else if (m.initialDecl() != null) {
                initial = m.initialDecl().ID().getText();
            } else if (m.varDecl() != null) {
                vars.put(m.varDecl().ID().getText(), Integer.parseInt(m.varDecl().INT().getText()));
            } else {
                transitions.add(transition(m.transition()));
            }
        }
        return new Service(c.ID().getText(), states, initial, vars, transitions, c.getStart().getLine());
    }

    private Transition transition(TransitionContext c) {
        List<Action> actions = new ArrayList<>();
        for (ActionContext a : c.action()) {
            if (a instanceof GotoActionContext g) {
                actions.add(new Action.Goto(g.ID().getText()));
            } else if (a instanceof SendActionContext s) {
                actions.add(new Action.Send(msgRef(s.msgRef())));
            } else if (a instanceof AssignActionContext as) {
                actions.add(new Action.Assign(as.ID().getText(), expr(as.expr())));
            }
        }
        return new Transition(c.trigger().getText(), c.ID().getText(), actions, c.getStart().getLine());
    }

    private Bounds bounds(BoundsDeclContext c) {
        Map<String, Integer> v = new HashMap<>(
            Map.of("maxDepth", 20, "maxInFlight", 4, "maxLost", 0, "maxDuplicated", 0));
        for (int i = 0; i < c.ID().size(); i++) {
            String key = c.ID(i).getText();
            if (!v.containsKey(key)) {
                throw new MishapSyntaxException(
                    "line " + c.ID(i).getSymbol().getLine() + ": unknown bound '" + key + "'");
            }
            v.put(key, Integer.parseInt(c.INT(i).getText()));
        }
        return new Bounds(v.get("maxDepth"), v.get("maxInFlight"), v.get("maxLost"), v.get("maxDuplicated"));
    }

    private Property property(PropertyContext c) {
        Property.Kind kind = c.kind.getText().equals("invariant")
            ? Property.Kind.INVARIANT : Property.Kind.FINAL;
        return new Property(kind, c.ID().getText(), expr(c.expr()), c.getStart().getLine());
    }

    private Expr expr(ExprContext c) {
        return new ExprVisitor().visit(c);
    }

    private static final class ExprVisitor extends MishapBaseVisitor<Expr> {
        @Override public Expr visitAddSub(AddSubContext c) {
            return new Expr.Bin(c.op.getText(), visit(c.expr(0)), visit(c.expr(1)));
        }
        @Override public Expr visitCompare(CompareContext c) {
            return new Expr.Bin(c.op.getText(), visit(c.expr(0)), visit(c.expr(1)));
        }
        @Override public Expr visitNot(NotContext c) {
            return new Expr.Not(visit(c.expr()));
        }
        @Override public Expr visitAnd(AndContext c) {
            return new Expr.Bin("and", visit(c.expr(0)), visit(c.expr(1)));
        }
        @Override public Expr visitOr(OrContext c) {
            return new Expr.Bin("or", visit(c.expr(0)), visit(c.expr(1)));
        }
        @Override public Expr visitParens(ParensContext c) {
            return visit(c.expr());
        }
        @Override public Expr visitIntLit(IntLitContext c) {
            return new Expr.IntLit(Integer.parseInt(c.INT().getText()));
        }
        @Override public Expr visitRefExpr(RefExprContext c) {
            String[] parts = c.ref().getText().split("\\.");
            return new Expr.Ref(parts[0], parts[1]);
        }
        @Override public Expr visitNameExpr(NameExprContext c) {
            return new Expr.Name(c.ID().getText());
        }
    }
}