package io.github.aurellya.mishap.model;

public record MsgRef(String service, String message) {
    @Override public String toString() { return service + "." + message; }
}