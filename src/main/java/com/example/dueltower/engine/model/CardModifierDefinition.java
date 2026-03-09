package com.example.dueltower.engine.model;

public record CardModifierDefinition(
        String id,
        String name,
        int priority,
        String description
) {}

