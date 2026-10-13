package io.github.aurellya.mishap.explorer;

import io.github.aurellya.mishap.model.*;

import java.util.*;
import java.util.stream.Collectors;

public final class Explorer {

    private record Step(String description, State next) {}
    private record Node(State state, Node parent, String step, int depth) {}

    private final Spec spec;
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final Set<String> lossy;
    private boolean truncated;

    public Explorer(Spec spec) {
        this.spec = spec;
        spec.services().forEach(s -> services.put(s.name(), s));
        this.lossy = spec.lossy().stream().map(MsgRef::toString).collect(Collectors.toSet());
    }

    public Result run() {
        State init = initialState();
        Set<State> visited = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>();
        Map<String, Violation> found = new LinkedHashMap<>();

        Node root = new Node(init, null, null, 0);
        visited.add(init);
        queue.add(root);
        check(root, found);
        int explored = 1;

        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n.depth() >= spec.bounds().maxDepth()) {
                if (!isFinal(n.state())) truncated = true;
                continue;
            }
            for (Step st : successors(n.state())) {
                if (!visited.add(st.next())) continue;
                Node child = new Node(st.next(), n, st.description(), n.depth() + 1);
                explored++;
                check(child, found);
                queue.add(child);
            }
        }
        return new Result(spec, explored, new ArrayList<>(found.values()), truncated);
    }

    private State initialState() {
        Map<String, String> loc = new TreeMap<>();
        Map<String, Integer> vars = new TreeMap<>();
        for (Service s : spec.services()) {
            loc.put(s.name(), s.initial());
            s.vars().forEach((k, v) -> vars.put(s.name() + "." + k, v));
        }
        Map<String, Integer> inflight = new TreeMap<>();
        spec.init().forEach(r -> inflight.merge(r.toString(), 1, Integer::sum));
        return new State(loc, vars, inflight, 0, 0);
    }

    private void check(Node node, Map<String, Violation> found) {
        for (Property p : spec.properties()) {
            if (found.containsKey(p.name())) continue;       // BFS: first hit is the shortest trace
            boolean applies = p.kind() == Property.Kind.INVARIANT || isFinal(node.state());
            if (applies && !Evaluator.holds(p.expr(), node.state())) {
                found.put(p.name(), new Violation(p, trace(node)));
            }
        }
    }

    private List<String> trace(Node node) {
        LinkedList<String> steps = new LinkedList<>();
        for (Node n = node; n.parent() != null; n = n.parent()) steps.addFirst(n.step());
        return steps;
    }

    /** Final: nothing in flight and no timeout enabled. */
    private boolean isFinal(State s) {
        return s.inflight().isEmpty() && enabledTimeouts(s).isEmpty();
    }

    private List<Map.Entry<Service, Transition>> enabledTimeouts(State s) {
        List<Map.Entry<Service, Transition>> out = new ArrayList<>();
        for (Service svc : spec.services()) {
            String cur = s.loc().get(svc.name());
            for (Transition t : svc.transitions()) {
                if (t.isTimeout() && t.state().equals(cur)) out.add(Map.entry(svc, t));
            }
        }
        return out;
    }

    private List<Step> successors(State s) {
        List<Step> out = new ArrayList<>();

        // 1. deliver one in-flight message
        for (String key : s.inflight().keySet()) {
            add(out, deliver(s, key));
        }
        // 2. fire a timeout
        for (var e : enabledTimeouts(s)) {
            Service svc = e.getKey();
            add(out, apply(s, svc, e.getValue(), new TreeMap<>(s.inflight()),
                    svc.name() + " times out in " + s.loc().get(svc.name())));
        }
        // 3. lose a lossy message
        if (spec.failures().contains("lose") && s.lost() < spec.bounds().maxLost()) {
            for (String key : s.inflight().keySet()) {
                if (!lossy.contains(key)) continue;
                Map<String, Integer> in = new TreeMap<>(s.inflight());
                decrement(in, key);
                out.add(new Step("LOST: message " + key + " disappears in transit",
                        new State(s.loc(), s.vars(), in, s.lost() + 1, s.duplicated())));
            }
        }
        // 4. duplicate a message
        if (spec.failures().contains("duplicate") && s.duplicated() < spec.bounds().maxDuplicated()) {
            for (String key : s.inflight().keySet()) {
                if (s.inFlightCount() + 1 > spec.bounds().maxInFlight()) { truncated = true; continue; }
                Map<String, Integer> in = new TreeMap<>(s.inflight());
                in.merge(key, 1, Integer::sum);
                out.add(new Step("DUPLICATED: message " + key + " will be delivered twice",
                        new State(s.loc(), s.vars(), in, s.lost(), s.duplicated() + 1)));
            }
        }
        return out;
    }

    private Step deliver(State s, String key) {
        String[] p = key.split("\\.");
        String svcName = p[0], msg = p[1];
        Service svc = services.get(svcName);
        String cur = s.loc().get(svcName);

        Map<String, Integer> in = new TreeMap<>(s.inflight());
        decrement(in, key);

        Transition t = svc.transitions().stream()
                .filter(x -> !x.isTimeout() && x.trigger().equals(msg) && x.state().equals(cur))
                .findFirst().orElse(null);

        String head = svcName + " receives " + msg + " in " + cur;
        if (t == null) {
            return new Step(head + ": no transition, message dropped",
                    new State(s.loc(), s.vars(), in, s.lost(), s.duplicated()));
        }
        return apply(s, svc, t, in, head);
    }

    /** Applies a transition. Expressions read the state BEFORE the transition. Returns null if bounds are exceeded. */
    private Step apply(State s, Service svc, Transition t, Map<String, Integer> inflight, String head) {
        Map<String, String> loc = new TreeMap<>(s.loc());
        Map<String, Integer> vars = new TreeMap<>(s.vars());
        List<String> effects = new ArrayList<>();

        for (Action a : t.actions()) {
            switch (a) {
                case Action.Goto g -> {
                    loc.put(svc.name(), g.state());
                    effects.add("goto " + g.state());
                }
                case Action.Assign as -> {
                    int v = Evaluator.num(Evaluator.eval(as.value(), s, svc.name()));
                    vars.put(svc.name() + "." + as.variable(), v);
                    effects.add(as.variable() + " = " + v);
                }
                case Action.Send snd -> {
                    inflight.merge(snd.target().toString(), 1, Integer::sum);
                    effects.add("send " + snd.target());
                }
            }
        }

        int total = inflight.values().stream().mapToInt(Integer::intValue).sum();
        if (total > spec.bounds().maxInFlight()) {
            truncated = true;
            return null;
        }

        String desc = effects.isEmpty() ? head : head + " -> " + String.join(", ", effects);
        return new Step(desc, new State(loc, vars, inflight, s.lost(), s.duplicated()));
    }

    private static void add(List<Step> list, Step step) {
        if (step != null) list.add(step);
    }

    private static void decrement(Map<String, Integer> m, String key) {
        m.computeIfPresent(key, (k, v) -> v == 1 ? null : v - 1);
    }
}