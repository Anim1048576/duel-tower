package com.example.dueltower.content.passive.model;

import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.model.PassiveDefinition;

public interface PassiveBlueprint extends PassiveEffect {
    PassiveDefinition definition();
}
