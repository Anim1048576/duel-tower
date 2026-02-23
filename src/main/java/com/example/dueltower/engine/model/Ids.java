package com.example.dueltower.engine.model;

import java.util.UUID;

public final class Ids {
    private Ids() {}

    public record SessionId(UUID value) {}
    public record PlayerId(String value) {}
    public record CardDefId(String value) {}
    public record CardInstId(UUID value) {}

    public static CardInstId newCardInstId() {
        return new CardInstId(UUID.randomUUID());
    }
}