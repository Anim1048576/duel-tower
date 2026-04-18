package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.DiscardFilter;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.card.CardCostCtx;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Tig005_Card implements CardBlueprint {
    @Override public String id() { return "Tig005_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "소닉 블레이드",
                CardType.SKILL,
                2,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        패를 1장 버리고 적 전체에게 공격력+{극복}피해를 입힌다.
                        극복이 3이상인 경우, 코스트가 1감소한다.
                        """
        );
    }

    @Override
    public int onCost(
            CardCostCtx ctx,
            int currentCost
    ) {
        GameState state = ctx.state();
        PlayerState me = state.player(TargetRef.requirePlayer(ctx.actor()));
        if (me == null) return currentCost;

        return TigEffectSupport.isOvercome3Plus(me)
                ? Math.max(0, currentCost - 1)
                : currentCost;
    }

    @Override
    public CardPlaySpec playSpec() {
        return new CardPlaySpec(
                TargetSpec.none(),
                List.of(new DiscardFromHandRequirement(1, true, DiscardFilter.ANY))
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        PlayerState me = ec.state().player(ec.actor());
        List<String> errors = new ArrayList<>();
        TigEffectSupport.validateSingleDiscardSelection(ec, me, errors);
        return errors;
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        PlayerState me = ec.state().player(ec.actor());

        if (!TigEffectSupport.discardSelectedOrAbort(ec, me)) return;

        int overcome = TigEffectSupport.overcome(me);
        ops.damageWithActorAttackPlus(overcome, Target.ENEMY_ALL);
    }
}
