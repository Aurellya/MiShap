package io.github.aurellya.mishap.model;

public record Bounds(int maxDepth, int maxInFlight, int maxLost, int maxDuplicated) {}