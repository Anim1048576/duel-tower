package com.example.dueltower.content.cardmodifier.model;

import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierEffect;
import com.example.dueltower.engine.model.CardModifierDefinition;

public interface CardModifierBlueprint extends CardModifierEffect {
    CardModifierDefinition definition();
}
