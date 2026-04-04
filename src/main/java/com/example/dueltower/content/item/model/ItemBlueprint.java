package com.example.dueltower.content.item.model;

import com.example.dueltower.engine.core.effect.item.ItemEffect;
import com.example.dueltower.engine.model.ItemDefinition;

public interface ItemBlueprint extends ItemEffect {
    ItemDefinition definition();
}
