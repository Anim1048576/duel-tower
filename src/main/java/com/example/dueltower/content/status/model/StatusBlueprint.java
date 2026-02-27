package com.example.dueltower.content.status.model;

import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.model.StatusDefinition;

public interface StatusBlueprint extends StatusEffect {
    StatusDefinition definition();
}
