package com.example.dueltower.engine.core;

import com.example.dueltower.content.keyword.kdb.K014_LastWords;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.keyword.DiscardReason;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;

import java.util.List;

public final class EffectDiscardOps {
    private EffectDiscardOps() {}

    public static boolean discardFromHandByEffect(EffectContext ec, PlayerState owner, Ids.CardInstId targetId) {
        if (ec == null || owner == null || targetId == null) {
            return false;
        }
        if (!owner.hand().contains(targetId)) {
            return false;
        }

        List<String> errors = KeywordOps.validateDiscard(ec.state(), ec.ctx(), owner, targetId, DiscardReason.EFFECT);
        if (!errors.isEmpty()) {
            return false;
        }

        ZoneOps.moveToZoneOrVanishIfToken(ec.state(), ec.ctx(), owner, targetId, Zone.GRAVE, ec.out(), MoveReason.DISCARD);
        int lastWordsValue = KeywordOps.keywordValue(ec.state(), ec.ctx(), targetId, K014_LastWords.ID);
        if (lastWordsValue > 0) {
            ec.lastWordsBatchCollector().register(targetId);
        }
        return true;
    }
}
