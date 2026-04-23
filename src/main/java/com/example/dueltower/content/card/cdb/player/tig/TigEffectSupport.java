package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.status.sdb.player.tig.Tig201_Status;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EffectDiscardOps;
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

    static boolean isOvercomeAtLeast(PlayerState me, int threshold) {
        return overcome(me) >= threshold;
    }

    static boolean isOvercome3Plus(PlayerState me) {
        return isOvercomeAtLeast(me, 3);
    }

    static List<Ids.CardInstId> discardCandidatesExcludingSource(EffectContext ec, PlayerState me) {
        List<Ids.CardInstId> hand = new ArrayList<>(me.hand());
        List<Ids.CardInstId> candidates = new ArrayList<>();
        for (Ids.CardInstId id : hand) {
            if (id.equals(ec.cardId())) continue;
            if (!KeywordOps.validateDiscard(ec.state(), ec.ctx(), me, id, DiscardReason.EFFECT).isEmpty()) continue;
            candidates.add(id);
        }
        return candidates;
    }

    static boolean validateSingleDiscardSelection(EffectContext ec, PlayerState me, List<String> errors) {
        List<Ids.CardInstId> selected = ec.discardIds();
        if (selected.size() != 1) {
            errors.add("discardIds must contain exactly 1 card");
            return false;
        }

        Ids.CardInstId selectedId = selected.get(0);
        if (!me.hand().contains(selectedId)) {
            errors.add("discard card not in hand: " + selectedId.value());
            return false;
        }
        if (selectedId.equals(ec.cardId())) {
            errors.add("source card cannot be selected for discard: " + selectedId.value());
            return false;
        }

        KeywordOps.validateDiscard(ec.state(), ec.ctx(), me, selectedId, DiscardReason.EFFECT, errors);
        if (!errors.isEmpty()) {
            return false;
        }

        List<Ids.CardInstId> candidates = discardCandidatesExcludingSource(ec, me);
        if (candidates.isEmpty()) {
            errors.add("no effect-discardable cards in hand");
            return false;
        }
        return true;
    }

    static boolean discardSelectedOrAbort(EffectContext ec, PlayerState me) {
        List<String> errors = new ArrayList<>();
        if (!validateSingleDiscardSelection(ec, me, errors)) return false;
        Ids.CardInstId selectedId = ec.discardIds().get(0);
        return EffectDiscardOps.discardFromHandByEffect(ec, me, selectedId);
    }

    static boolean discardOneFromHandExcludingSource(EffectContext ec, PlayerState me) {
        List<Ids.CardInstId> candidates = discardCandidatesExcludingSource(ec, me);
        if (candidates.isEmpty()) return false;
        return EffectDiscardOps.discardFromHandByEffect(ec, me, candidates.get(0));
    }

    static boolean requireDiscardOrAbort(EffectContext ec, PlayerState me, String cardId) {
        if (discardOneFromHandExcludingSource(ec, me)) return true;
        log(ec, cardId + ": discard failed, no card to discard");
        return false;
    }

    static List<Ids.CardInstId> installedCardCandidates(EffectContext ec, boolean excludeSourceCard) {
        List<Ids.CardInstId> candidates = new ArrayList<>();
        for (PlayerState ps : ec.state().players().values()) {
            List<Ids.CardInstId> field = new ArrayList<>(ps.field());
            for (Ids.CardInstId id : field) {
                if (excludeSourceCard && id.equals(ec.cardId())) continue;
                if (!KeywordOps.hasKeyword(ec.state(), ec.ctx(), id, K003_Installed.ID)) continue;
                candidates.add(id);
            }
        }
        return candidates;
    }

    static void validateInstalledCardSelection(EffectContext ec, int minSelections, int maxSelections, List<String> errors) {
        List<Ids.CardInstId> selected = ec.selectedIds();
        int count = selected.size();
        if (count < minSelections || count > maxSelections) {
            errors.add("selectedIds must contain between " + minSelections + " and " + maxSelections + " cards");
            return;
        }

        List<Ids.CardInstId> candidates = installedCardCandidates(ec, true);
        var unique = new java.util.HashSet<Ids.CardInstId>();
        for (Ids.CardInstId id : selected) {
            if (!unique.add(id)) {
                errors.add("duplicate selected card: " + id.value());
                continue;
            }
            if (!candidates.contains(id)) {
                errors.add("selected installed-destroy target is invalid: " + id.value());
            }
        }
    }

    static int destroySelectedInstalledCards(EffectContext ec) {
        int destroyed = 0;
        GameState state = ec.state();
        EngineContext ctx = ec.ctx();
        for (Ids.CardInstId id : ec.selectedIds()) {
            CardInstance card = state.card(id);
            if (card == null) continue;
            Ids.PlayerId ownerId = card.ownerId();
            if (ownerId == null) continue;
            PlayerState owner = state.player(ownerId);
            if (owner == null) continue;
            if (!owner.field().contains(id)) continue;
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, owner, id, Zone.GRAVE, ec.out());
            destroyed++;
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
