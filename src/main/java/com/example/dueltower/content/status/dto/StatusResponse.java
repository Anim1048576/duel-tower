package com.example.dueltower.content.status.dto;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.StatusKind;
import com.example.dueltower.engine.model.StatusScope;
import com.example.dueltower.engine.model.StatusTag;
import com.example.dueltower.engine.model.StatusVisibility;

import java.util.Set;

public record StatusResponse(
        String id,
        String name,
        StatusKind kind,
        StatusScope scope,
        Set<StatusTag> tags,
        int priority,
        boolean persistsAfterCombat,
        String description,
        StatusVisibility visibility,
        String contentOwner
) {
    public static StatusResponse of(StatusDefinition definition, String contentOwner) {
        return new StatusResponse(
                definition.id(),
                definition.name(),
                definition.kind(),
                definition.scope(),
                definition.tags(),
                definition.priority(),
                definition.persistsAfterCombat(),
                definition.description(),
                definition.visibility(),
                contentOwner == null || contentOwner.isBlank() ? ContentOwnerIds.COMMON : contentOwner
        );
    }
}
