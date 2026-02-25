package com.example.dueltower.engine.core.effect;

import java.util.List;

public interface CardEffect {
    String id(); // "C001"

    default List<String> validate(EffectContext ec) {
        return List.of();
    }

    void resolve(EffectContext ec);
}