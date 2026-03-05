package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

public class Tig008_Card implements CardBlueprint {
    @Override public String id() { return "Tig008_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "오버 드라이브",
                CardType.SKILL,
                3,
                Map.of(
                        K003_Installed.ID, 1
                ),
                Zone.FIELD,
                false,
                """
                        패를 1장 버리고 설치한다, 자신의 공격력이 4 상승한다.
                        극복이 3이상인 경우, 설치시 적 하나에게 {공격력}+{극복}피해를 입힌다.
                        """
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        // TODO : 효과 구현
    }
}
