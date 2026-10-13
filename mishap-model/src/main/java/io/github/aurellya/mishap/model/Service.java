package io.github.aurellya.mishap.model;

import java.util.List;
import java.util.Map;

public record Service(String name, List<String> states, String initial,
                      Map<String, Integer> vars, List<Transition> transitions, int line) {}