package com.example.dueltower.engine.core;

import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.Map;

public final class EngineContext {
    private final Map<CardDefId, CardDefinition> definitions;

    public EngineContext(Map<CardDefId, CardDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public CardDefinition def(CardDefId id) {
        CardDefinition d = definitions.get(id);
        if (d == null) throw new IllegalArgumentException("missing card definition: " + id.value());
        return d;
    }
}