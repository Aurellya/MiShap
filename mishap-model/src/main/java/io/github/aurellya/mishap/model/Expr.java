package io.github.aurellya.mishap.model;

public sealed interface Expr {
    record IntLit(int value) implements Expr {}
    record Name(String name) implements Expr {} // state name or variable
    record Ref(String service, String member) implements Expr {} // Payment.charges, Order.state
    record Not(Expr operand) implements Expr {}
    record Bin(String op, Expr left, Expr right) implements Expr {} // + - == != <= >= < > and or
}