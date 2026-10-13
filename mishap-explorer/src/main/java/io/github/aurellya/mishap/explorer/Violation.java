package io.github.aurellya.mishap.explorer;

import io.github.aurellya.mishap.model.Property;

import java.util.List;

public record Violation(Property property, List<String> trace) {}