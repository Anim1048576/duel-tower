package com.example.dueltower.content.status;

import com.example.dueltower.engine.core.status.StatusEffect;
import com.example.dueltower.engine.model.StatusDefinition;

public interface StatusBlueprint extends StatusEffect {
    StatusDefinition definition();
}
