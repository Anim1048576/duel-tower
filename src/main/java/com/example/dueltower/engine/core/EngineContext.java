package com.example.dueltower.engine.core;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.effect.CardEffectResolver;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.Map;

public final class EngineContext {
    private final Map<CardDefId, CardDefinition> definitions;
    private final CardEffectResolver effects;

    public EngineContext(Map<CardDefId, CardDefinition> definitions, CardEffectResolver effects) {
        this.definitions = Map.copyOf(definitions);
        this.effects = effects;
    }

    public CardDefinition def(CardDefId id) {
        CardDefinition d = definitions.get(id);
        if (d == null) throw new IllegalArgumentException("missing card definition: " + id.value());
        return d;
    }

    public boolean hasEffect(String effectId) {
        return effects.exists(effectId);
    }

    public CardEffect effect(String effectId) {
        return effects.resolve(effectId);
    }
}