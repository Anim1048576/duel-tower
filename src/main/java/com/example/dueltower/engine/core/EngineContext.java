package com.example.dueltower.engine.core;

import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.StatusDefinition;

import java.util.Map;

/**
 * Engine-wide immutable lookup context for a session.
 * - card definitions/effects (cdb)
 * - status definitions/effects (sdb)
 * - keyword definitions/effects (kdb)
 */
public final class EngineContext {
    private final Map<CardDefId, CardDefinition> definitions;
    private final Map<CardDefId, CardEffect> effects;

    private final Map<String, StatusDefinition> statusDefs;
    private final Map<String, StatusEffect> statusEffects;

    private final Map<String, KeywordDefinition> keywordDefs;
    private final Map<String, KeywordEffect> keywordEffects;

    public EngineContext(Map<CardDefId, CardDefinition> definitions, Map<CardDefId, CardEffect> effects) {
        this(definitions, effects, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects
    ) {
        this(definitions, effects, statusDefs, statusEffects, Map.of(), Map.of());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects
    ) {
        this.definitions = Map.copyOf(definitions);
        this.effects = Map.copyOf(effects);
        this.statusDefs = Map.copyOf(statusDefs);
        this.statusEffects = Map.copyOf(statusEffects);
        this.keywordDefs = Map.copyOf(keywordDefs);
        this.keywordEffects = Map.copyOf(keywordEffects);
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

    public boolean hasKeywordDef(String id) {
        return keywordDefs.containsKey(id);
    }

    public KeywordDefinition keywordDef(String id) {
        KeywordDefinition d = keywordDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing KeywordDefinition: " + id);
        return d;
    }

    public boolean hasKeywordEffect(String id) {
        return keywordEffects.containsKey(id);
    }

    public KeywordEffect keywordEffect(String id) {
        KeywordEffect e = keywordEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing KeywordEffect: " + id);
        return e;
    }
}
