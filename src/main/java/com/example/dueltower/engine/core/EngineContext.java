package com.example.dueltower.engine.core;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.Map;

public final class EngineContext {
    private final Map<CardDefId, CardDefinition> definitions;
    private final Map<CardDefId, CardEffect> effects;

    public EngineContext(Map<CardDefId, CardDefinition> definitions, Map<CardDefId, CardEffect> effects) {
        this.definitions = Map.copyOf(definitions);
        this.effects = Map.copyOf(effects);
    }

    public CardDefinition def(CardDefId id) {
        CardDefinition d = definitions.get(id);
        if (d == null) throw new IllegalArgumentException("missing card definition: " + id.value());
        return d;
    }

    public boolean hasEffect(CardDefId defId) {
        return effects.containsKey(defId);
    }

    public CardEffect effect(CardDefId defId) {
        CardEffect e = effects.get(defId);
        if (e == null) throw new IllegalArgumentException("missing CardEffect: " + defId.value());
        return e;
    }
}