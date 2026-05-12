package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.*;

public record StatusApplyContext(
        GameState state,
        EngineContext ctx,
        TargetRef source,
        StatusOwnerRef owner,
        String statusId,
        int baseAmount,
        Ids.CardInstId sourceCardId,
        CardDefinition sourceCardDef,
        String sourceLabel,
        StatusApplySourceKind sourceKind
) {
    public StatusApplyContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (ctx == null) throw new IllegalArgumentException("ctx is required");
        if (owner == null) throw new IllegalArgumentException("owner is required");
        if (statusId == null || statusId.isBlank()) {
            throw new IllegalArgumentException("statusId is required");
        }
        statusId = statusId.trim();
        sourceLabel = (sourceLabel == null || sourceLabel.isBlank()) ? "SYSTEM" : sourceLabel;
        sourceKind = (sourceKind == null) ? StatusApplySourceKind.SYSTEM : sourceKind;
    }
}
