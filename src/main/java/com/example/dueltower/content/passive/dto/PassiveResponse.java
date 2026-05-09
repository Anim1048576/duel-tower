package com.example.dueltower.content.passive.dto;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.engine.model.PassiveDefinition;

public record PassiveResponse(
        String id,
        String name,
        int priority,
        String description,
        String contentOwner
) {
    public static PassiveResponse of(PassiveDefinition definition, String contentOwner) {
        return new PassiveResponse(
                definition.id(),
                definition.name(),
                definition.priority(),
                definition.description(),
                contentOwner == null || contentOwner.isBlank() ? ContentOwnerIds.COMMON : contentOwner
        );
    }
}
