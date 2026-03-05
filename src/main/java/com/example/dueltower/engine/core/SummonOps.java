package com.example.dueltower.engine.core;

import com.example.dueltower.content.keyword.kdb.K004_Summon;
import com.example.dueltower.content.keyword.kdb.K901_SummonHp;
import com.example.dueltower.content.keyword.kdb.K902_SummonAttackPower;
import com.example.dueltower.content.keyword.kdb.K903_SummonHealingPower;
import com.example.dueltower.content.keyword.kdb.K904_Action;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

public final class SummonOps {
    private SummonOps() {}

    public static SummonState spawnFromCard(GameState state, EngineContext ctx, PlayerState owner, CardInstId sourceCardId) {
        if (state == null || ctx == null || owner == null || sourceCardId == null) return null;
        CardInstance ci = state.card(sourceCardId);
        if (ci == null || ci.zone() != Zone.FIELD) return null;
        if (!KeywordOps.hasKeyword(state, ctx, sourceCardId, K004_Summon.ID)) return null;

        SummonInstId summonId = new SummonInstId(sourceCardId.value());
        int hp = Math.max(1, KeywordOps.keywordValue(state, ctx, sourceCardId, K901_SummonHp.ID));
        int atk = Math.max(0, KeywordOps.keywordValue(state, ctx, sourceCardId, K902_SummonAttackPower.ID));
        int heal = Math.max(0, KeywordOps.keywordValue(state, ctx, sourceCardId, K903_SummonHealingPower.ID));
        int actionCost = Math.max(0, KeywordOps.keywordValue(state, ctx, sourceCardId, K904_Action.ID));

        SummonState summon = new SummonState(
                summonId,
                owner.playerId(),
                sourceCardId,
                hp,
                hp,
                atk,
                heal,
                actionCost,
                false
        );
        state.summons().put(summonId, summon);
        owner.summonByCard().put(sourceCardId, summonId);
        if (!owner.activeSummons().contains(summonId)) {
            owner.activeSummons().add(summonId);
        }
        return summon;
    }

    public static void destroySummonForCard(GameState state, PlayerState owner, CardInstId sourceCardId) {
        if (state == null || owner == null || sourceCardId == null) return;
        SummonInstId summonId = owner.summonByCard().remove(sourceCardId);
        if (summonId == null) return;
        owner.activeSummons().remove(summonId);
        state.summons().remove(summonId);
    }
}
