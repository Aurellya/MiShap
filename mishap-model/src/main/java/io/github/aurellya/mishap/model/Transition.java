package io.github.aurellya.mishap.model;

import java.util.List;

public record Transition(String trigger, String state, List<Action> actions, int line) {
    public boolean isTimeout() { return trigger.equals("timeout"); }
}