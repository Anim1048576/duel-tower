package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.status.sdb.player.tig.Tig201_Status;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.keyword.DiscardReason;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class TigEffectSupport {
    private TigEffectSupport() {}

    static int overcome(PlayerState me) {
        return me.status(Tig201_Status.ID);
    }

    static boolean discardOneFromHandExcludingSource(EffectContext ec, PlayerState me) {
        List<Ids.CardInstId> hand = new ArrayList<>(me.hand());
        for (Ids.CardInstId id : hand) {
            if (id.equals(ec.cardId())) continue;
            if (!KeywordOps.validateDiscard(ec.state(), ec.ctx(), me, id, DiscardReason.EFFECT).isEmpty()) continue;
            ZoneOps.moveToZoneOrVanishIfToken(ec.state(), ec.ctx(), me, id, Zone.GRAVE, ec.out());
            return true;
        }
        return false;
    }

    static boolean requireDiscardOrAbort(EffectContext ec, PlayerState me, String cardId) {
        if (discardOneFromHandExcludingSource(ec, me)) return true;
        log(ec, cardId + ": discard failed, no card to discard");
        return false;
    }

    static int destroyInstalledCards(EffectContext ec, int maxCount) {
        if (maxCount <= 0) return 0;

        int destroyed = 0;
        for (PlayerState ps : ec.state().players().values()) {
            List<Ids.CardInstId> field = new ArrayList<>(ps.field());
            for (Ids.CardInstId id : field) {
                if (destroyed >= maxCount) return destroyed;
                if (id.equals(ec.cardId())) continue;
                if (!KeywordOps.hasKeyword(ec.state(), ec.ctx(), id, K003_Installed.ID)) continue;
                ZoneOps.moveToZoneOrVanishIfToken(ec.state(), ec.ctx(), ps, id, Zone.GRAVE, ec.out());
                destroyed++;
            }
        }
        return destroyed;
    }

    static int rollD6(EffectContext ec) {
        long mix = ec.state().seed() ^ ec.state().version() ^ (long) ec.out().size() * 31L;
        mix ^= ec.actor().value().hashCode();
        mix ^= ec.cardId().value().hashCode();
        return DiceUtility.rollDice(1, 6, new Random(mix));
    }

    static void log(EffectContext ec, String msg) {
        ec.out().add(new GameEvent.LogAppended(msg));
    }
}
