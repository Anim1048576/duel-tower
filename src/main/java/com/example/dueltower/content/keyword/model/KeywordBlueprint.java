package com.example.dueltower.content.keyword.model;

import com.example.dueltower.content.meta.ContentOwned;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.model.KeywordDefinition;

public interface KeywordBlueprint extends KeywordEffect, ContentOwned {
    KeywordDefinition definition();
}
