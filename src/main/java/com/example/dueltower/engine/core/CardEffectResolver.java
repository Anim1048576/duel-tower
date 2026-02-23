package com.example.dueltower.engine.core;

public interface CardEffectResolver {
    boolean exists(String effectId);
    CardEffect resolve(String effectId);
}
