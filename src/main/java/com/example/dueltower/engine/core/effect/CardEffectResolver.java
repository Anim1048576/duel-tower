package com.example.dueltower.engine.core.effect;

public interface CardEffectResolver {
    boolean exists(String effectId);
    CardEffect resolve(String effectId);
}
