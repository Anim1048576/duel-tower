package com.example.dueltower.content.card.cdb;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 기본 회복 [코스트:1]
 * 효과: 아군 1명의 체력을 {치유력} 만큼 회복한다.
 */
@Component
public class C002_BasicRecovery implements CardBlueprint {
    @Override public String id() { return "C002"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "기본 치유",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        아군 1명의 체력을 {치유력} 만큼 회복한다.
                        """
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        // 아군 1명 선택이 필요하다면
        return new EffectOps(ec).validateTarget(Target.ALLY_ONE);
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);

        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());

        int heal = me.healPower();
        ops.heal(Target.ALLY_ONE, heal);
    }
}