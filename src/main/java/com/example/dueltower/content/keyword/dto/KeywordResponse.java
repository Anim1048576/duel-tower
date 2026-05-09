package com.example.dueltower.content.keyword.dto;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.KeywordRole;

public record KeywordResponse(
        String id,
        String name,
        boolean parameterized,
        String description,
        KeywordRole role,
        String parentKeywordId,
        String contentOwner
) {
    public static KeywordResponse of(KeywordDefinition definition, String contentOwner) {
        return new KeywordResponse(
                definition.id(),
                definition.name(),
                definition.parameterized(),
                definition.description(),
                definition.role(),
                definition.parentKeywordId(),
                contentOwner == null || contentOwner.isBlank() ? ContentOwnerIds.COMMON : contentOwner
        );
    }
}
