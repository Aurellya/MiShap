package io.github.aurellya.mishap.explorer;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable global state. Maps are sorted copies, so equals/hashCode work for the visited set. */
public record State(Map<String, String> loc, // service -> current state
                    Map<String, Integer> vars, // "Service.var" -> value
                    Map<String, Integer> inflight, // "Service.Message" -> number of copies
                    int lost, int duplicated) {

    public State {
        loc = Collections.unmodifiableMap(new TreeMap<>(loc));
        vars = Collections.unmodifiableMap(new TreeMap<>(vars));
        inflight = Collections.unmodifiableMap(new TreeMap<>(inflight));
    }

    public int inFlightCount() {
        return inflight.values().stream().mapToInt(Integer::intValue).sum();
    }
}