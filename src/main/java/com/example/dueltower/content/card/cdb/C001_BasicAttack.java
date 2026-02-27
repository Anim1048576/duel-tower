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
 * 기본 공격 [코스트:1]
 * 효과: 적 1명에게 {공격력} 만큼의 대미지를 준다.
 */
@Component
public class C001_BasicAttack implements CardBlueprint {
    @Override public String id() { return "C001"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "기본 공격",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        적 1명에게 {공격력} 만큼의 대미지를 준다.
                        """
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        return new EffectOps(ec).validateTarget(Target.ENEMY_ONE);
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);

        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());

        int dmg = me.attackPower();
        ops.damage(Target.ENEMY_ONE, dmg);
    }
}