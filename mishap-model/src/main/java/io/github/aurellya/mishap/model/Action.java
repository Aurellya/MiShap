package io.github.aurellya.mishap.model;

public sealed interface Action {
    record Goto(String state) implements Action {}
    record Send(MsgRef target) implements Action {}
    record Assign(String variable, Expr value) implements Action {}
}