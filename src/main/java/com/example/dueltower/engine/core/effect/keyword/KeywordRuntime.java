package com.example.dueltower.engine.core.effect.keyword;

public record KeywordRuntime(String id, int value) {
    public KeywordRuntime {
        id = (id == null) ? "" : id.trim();
    }

    public boolean present() {
        return !id.isEmpty() && value != 0;
    }

    public boolean flag() {
        return value != 0;
    }
}
