package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Tig901_EX implements CardBlueprint {

    @Override public String id() { return "Tig901_EX"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "맹호류 결착",
                CardType.EX,
                3,
                Map.of(),
                Zone.EX,
                false,
                """
                        적 둘에게 {공격력}+[극복] 피해를 입히고 1장 드로우한다.
                        또는, 적 하나에게 {공격력}+[극복] 피해를 2번 입히고 1장 드로우한다.
                        [극복]이 3이상인 경우, 공격 횟수를 1 증가시킨다.
                        """
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        // TODO : 효과 구현
    }
}
