package com.example.dueltower.content.keyword.model;

import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.model.KeywordDefinition;

public interface KeywordBlueprint extends KeywordEffect {
    KeywordDefinition definition();
}
