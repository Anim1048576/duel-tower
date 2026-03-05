package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

public class Tig003_Card implements CardBlueprint {
    @Override public String id() { return "Tig003_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "여우류 신속베기",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        패를 1장 버리고, 1D6을 굴려 4이상의 값이 나온 경우 3장 드로우 한다.
                        극복이 3이상인 경우, 추가로 적 1명에게 [취약]을 3 부여한다.
                        """
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        // TODO : 효과 구현
    }
}
