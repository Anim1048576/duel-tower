package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.playspec.BoardObjectKind;
import com.example.dueltower.content.card.model.playspec.BoardObjectRelation;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.DiscardFilter;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.content.card.model.playspec.SelectBoardObjectsRequirement;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.engine.core.EffectDiscardOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.keyword.DiscardReason;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class NamelessEffectSupport {
    static final int MAX_DECK_COPIES = 2;

    private NamelessEffectSupport() {
    }

    static CardDefinition skillDefinition(String id, String name, int cost, String description) {
        return new CardDefinition(
                new Ids.CardDefId(id),
                name,
                CardType.SKILL,
                cost,
                Map.of(),
                Zone.GRAVE,
                false,
                description
        );
    }

    static CardPlaySpec anyOnePlaySpec() {
        return new CardPlaySpec(
                TargetSpec.none(),
                List.of(new SelectBoardObjectsRequirement(
                        1,
                        1,
                        List.of(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON),
                        BoardObjectRelation.ANY,
                        null,
                        false
                ))
        );
    }

    static CardPlaySpec anyOneWithDiscardPlaySpec() {
        return new CardPlaySpec(
                TargetSpec.none(),
                List.of(
                        new SelectBoardObjectsRequirement(
                                1,
                                1,
                                List.of(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON),
                                BoardObjectRelation.ANY,
                                null,
                                false
                        ),
                        new DiscardFromHandRequirement(1, true, DiscardFilter.ANY)
                )
        );
    }

    static boolean selectedTargetIsAlly(EffectContext ec) {
        TargetRef chosen = ec.selection().requireOne();
        return CombatState.factionOf(ec.actorRef()) == CombatState.factionOf(chosen);
    }

    static List<String> validateAnyOneTarget(EffectContext ec) {
        return new com.example.dueltower.engine.core.effect.EffectOps(ec).validateTarget(Target.ANY_ONE);
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
        return errors.isEmpty();
    }

    static boolean discardSelectedOrAbort(EffectContext ec, PlayerState me) {
        List<String> errors = new ArrayList<>();
        if (!validateSingleDiscardSelection(ec, me, errors)) return false;
        return EffectDiscardOps.discardFromHandByEffect(ec, me, ec.discardIds().get(0));
    }
}
