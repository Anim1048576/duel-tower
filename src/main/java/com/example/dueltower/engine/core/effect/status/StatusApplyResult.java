package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.model.StatusOwnerRef;

public record StatusApplyResult(
        StatusOwnerRef owner,
        String statusId,
        int before,
        int requestedAmount,
        int modifiedAmount,
        int after,
        int actualAppliedAmount,
        boolean changed
) {}
