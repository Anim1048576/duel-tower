package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

public class Tig001_Card implements CardBlueprint {
    @Override public String id() { return "Tig001_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "사슴류 베기",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        적 1명에게 공격력+{극복} 피해를 입힌다.
                        극복이 3이상인 경우, 설치된 카드를 1장 파괴할 수 있다.
                        """
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        // TODO : 효과 구현
    }
}
