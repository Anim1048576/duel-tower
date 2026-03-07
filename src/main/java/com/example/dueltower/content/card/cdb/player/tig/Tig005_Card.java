package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

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
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        PlayerState me = ec.state().player(ec.actor());

        if (!TigEffectSupport.requireDiscardOrAbort(ec, me, id())) return;

        int overcome = TigEffectSupport.overcome(me);
        ops.damageWithActorAttackPlus(overcome, Target.ENEMY_ALL);

        // 코스트 감소 문구는 현재 상태/패시브/키워드 훅만 코스트 변형이 가능하므로, resolve 시 AP 1 환급으로 반영.
        if (TigEffectSupport.isOvercome3Plus(me)) me.ap(me.ap() + 1);
    }
}
