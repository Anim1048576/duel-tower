package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.core.EffectDiscardOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierRuntime;
import com.example.dueltower.engine.core.effect.cardmodifier.PlayCardModifierCtx;
import com.example.dueltower.engine.core.effect.keyword.DiscardReason;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class CM105_WeakenedDiscardOneSkill implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.WEAKENED_DISCARD_ONE_SKILL; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "약화: 사용 시 손패 폐기", 220, "카드 사용 시 다른 스킬 카드 1장을 버린다.");
    }

    @Override
    public void validatePlayCard(CardModifierRuntime rt, PlayCardModifierCtx c, List<String> errors) {
        if (discardCandidates(rt, c).isEmpty()) {
            errors.add("weakened discard: no discardable other skill card");
        }
    }

    @Override
    public void beforeResolvePlayCard(CardModifierRuntime rt, PlayCardModifierCtx c) {
        List<CardInstId> candidates = discardCandidates(rt, c);
        if (candidates.isEmpty()) return;
        CardInstId picked = candidates.get(0);
        EffectContext ec = new EffectContext(rt.state(), rt.ctx(), c.actorState().playerId(), c.cardId(), null, rt.out());
        EffectDiscardOps.discardFromHandByEffect(ec, c.actorState(), picked);
    }

    private List<CardInstId> discardCandidates(CardModifierRuntime rt, PlayCardModifierCtx c) {
        List<CardInstId> out = new ArrayList<>();
        for (CardInstId id : c.actorState().hand()) {
            if (id.equals(c.cardId())) continue;
            CardInstance handCard = rt.state().card(id);
            if (handCard == null) continue;
            CardDefinition def = rt.ctx().def(handCard.defId());
            if (def.type() != CardType.SKILL) continue;
            List<String> discardErrors = new ArrayList<>();
            KeywordOps.validateDiscard(rt.state(), rt.ctx(), c.actorState(), id, DiscardReason.EFFECT, discardErrors);
            if (!discardErrors.isEmpty()) continue;
            out.add(id);
        }
        out.sort(Comparator.comparing(CardInstId::value));
        return out;
    }
}
