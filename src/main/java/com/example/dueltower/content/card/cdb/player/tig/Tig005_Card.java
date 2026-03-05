package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

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
        // TODO : 효과 구현
    }
}
