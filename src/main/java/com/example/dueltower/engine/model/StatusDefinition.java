package com.example.dueltower.engine.model;

public record StatusDefinition(
        String id,
        String name,
        StatusKind kind,
        StatusScope scope,
        int priority,
        boolean persistsAfterCombat,
        String text
) {}
