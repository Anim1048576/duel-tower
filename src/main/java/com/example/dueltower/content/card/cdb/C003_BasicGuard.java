package com.example.dueltower.content.card.cdb;

import com.example.dueltower.content.card.CardBlueprint;
import com.example.dueltower.content.status.sdb.S001_Shield;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * 기본 방어 [코스트:1]
 * 효과: 자신은 {치유력} 만큼의 [보호]를 얻는다.
 */
@Component
public class C003_BasicGuard implements CardBlueprint {
    @Override public String id() { return "C003"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "기본 방어",
                CardType.SKILL,
                1,
                EnumSet.noneOf(Keyword.class),
                Zone.GRAVE,
                false,
                "자신은 {치유력} 만큼의 [보호]를 얻는다."
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);

        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());

        int shield = me.healPower();
        ops.addStatus(Target.SELF, S001_Shield.ID, shield);
    }
}
