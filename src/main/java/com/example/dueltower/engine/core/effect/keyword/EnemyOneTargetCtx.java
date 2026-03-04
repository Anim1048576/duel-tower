package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.TargetRef;

/**
 * Context for ENEMY_ONE target intent rules (e.g., TAUNT).
 * Used by keywords such as "명경" that can ignore taunt.
 */
public record EnemyOneTargetCtx(
        TargetRef actor,
        Ids.CardInstId cardId,
        TargetRef chosenEnemy
) {}
