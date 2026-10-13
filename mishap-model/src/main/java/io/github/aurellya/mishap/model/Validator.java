package io.github.aurellya.mishap.model;

import java.util.*;

public final class Validator {
    private Validator() {}

    /** Returns a list of human-readable errors, empty means the spec is valid. */
    public static List<String> validate(Spec spec) {
        List<String> errors = new ArrayList<>();
        Map<String, Service> byName = new HashMap<>();
        Set<String> allStates = new HashSet<>();

        for (Service s : spec.services()) {
            if (byName.put(s.name(), s) != null) {
                errors.add("line " + s.line() + ": duplicate service " + s.name());
            }
            allStates.addAll(s.states());
        }

        for (Service s : spec.services()) {
            checkService(s, byName, allStates, errors);
        }

        for (MsgRef r : spec.init()) checkMsg(r, byName, "init", errors);
        for (MsgRef r : spec.lossy()) checkMsg(r, byName, "lossy", errors);

        for (Property p : spec.properties()) {
            checkExpr(p.expr(), byName, allStates, "line " + p.line() + " (" + p.name() + ")", errors);
        }
        return errors;
    }

    private static void checkService(Service s, Map<String, Service> byName,
                                     Set<String> allStates, List<String> errors) {
        String where = "service " + s.name();
        Set<String> seen = new HashSet<>();
        for (String st : s.states()) {
            if (!seen.add(st)) errors.add("line " + s.line() + ": " + where + " declares state " + st + " twice");
        }
        if (s.initial() == null) {
            errors.add("line " + s.line() + ": " + where + " has no initial state");
        } else if (!s.states().contains(s.initial())) {
            errors.add("line " + s.line() + ": " + where + " initial state " + s.initial() + " is not declared");
        }

        Set<String> names = new HashSet<>(allStates);
        names.addAll(s.vars().keySet());

        for (Transition t : s.transitions()) {
            String at = "line " + t.line();
            if (!s.states().contains(t.state())) {
                errors.add(at + ": unknown state " + t.state() + " in " + where);
            }
            for (Action a : t.actions()) {
                switch (a) {
                    case Action.Goto g -> {
                        if (!s.states().contains(g.state())) {
                            errors.add(at + ": goto unknown state " + g.state() + " in " + where);
                        }
                    }
                    case Action.Send snd -> checkMsg(snd.target(), byName, at, errors);
                    case Action.Assign as -> {
                        if (!s.vars().containsKey(as.variable())) {
                            errors.add(at + ": assignment to undeclared variable " + as.variable());
                        }
                        checkExpr(as.value(), byName, names, at, errors);
                    }
                }
            }
        }
    }

    private static void checkMsg(MsgRef r, Map<String, Service> byName, String where, List<String> errors) {
        Service target = byName.get(r.service());
        if (target == null) {
            errors.add(where + ": unknown service " + r.service() + " in " + r);
        } else if (target.transitions().stream().noneMatch(t -> t.trigger().equals(r.message()))) {
            errors.add(where + ": service " + r.service() + " never handles message " + r.message());
        }
    }

    private static void checkExpr(Expr e, Map<String, Service> byName, Set<String> names,
                                  String where, List<String> errors) {
        switch (e) {
            case Expr.IntLit i -> { }
            case Expr.Name n -> {
                if (!names.contains(n.name())) errors.add(where + ": unknown name " + n.name());
            }
            case Expr.Ref r -> {
                Service s = byName.get(r.service());
                if (s == null) {
                    errors.add(where + ": unknown service " + r.service());
                } else if (!r.member().equals("state") && !s.vars().containsKey(r.member())) {
                    errors.add(where + ": " + r.service() + " has no variable " + r.member());
                }
            }
            case Expr.Not n -> checkExpr(n.operand(), byName, names, where, errors);
            case Expr.Bin b -> {
                checkExpr(b.left(), byName, names, where, errors);
                checkExpr(b.right(), byName, names, where, errors);
            }
        }
    }
}