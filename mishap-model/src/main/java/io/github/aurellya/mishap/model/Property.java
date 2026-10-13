package io.github.aurellya.mishap.model;

public record Property(Kind kind, String name, Expr expr, int line) {
    public enum Kind { INVARIANT, FINAL }
}