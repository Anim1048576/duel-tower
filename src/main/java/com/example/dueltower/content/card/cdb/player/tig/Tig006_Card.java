package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

public class Tig006_Card implements CardBlueprint {
    @Override public String id() { return "Tig006_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "크게 베기!!",
                CardType.SKILL,
                2,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        패 1장을 버리고 설치된 카드를 3장 까지 파괴한다.
                        """
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        // TODO : 효과 구현
    }
}
