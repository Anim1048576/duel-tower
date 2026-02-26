package com.example.dueltower.engine.core;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.status.StatusEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.Map;

public final class EngineContext {
    private final Map<CardDefId, CardDefinition> definitions;
    private final Map<CardDefId, CardEffect> effects;

    private final Map<String, StatusDefinition> statusDefs;
    private final Map<String, StatusEffect> statusEffects;

    public EngineContext(Map<CardDefId, CardDefinition> definitions, Map<CardDefId, CardEffect> effects) {
        this(definitions, effects, Map.of(), Map.of());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects
    ) {
        this.definitions = Map.copyOf(definitions);
        this.effects = Map.copyOf(effects);
        this.statusDefs = Map.copyOf(statusDefs);
        this.statusEffects = Map.copyOf(statusEffects);
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

    public boolean hasStatusDef(String id) {
        return statusDefs.containsKey(id);
    }

    public StatusDefinition statusDef(String id) {
        StatusDefinition d = statusDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing StatusDefinition: " + id);
        return d;
    }

    public boolean hasStatusEffect(String id) {
        return statusEffects.containsKey(id);
    }

    public StatusEffect statusEffect(String id) {
        StatusEffect e = statusEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing StatusEffect: " + id);
        return e;
    }
}
