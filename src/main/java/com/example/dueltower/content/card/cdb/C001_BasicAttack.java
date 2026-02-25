package com.example.dueltower.content.card.cdb;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기본 공격 [코스트:1]
 * 효과: 적 1명에게 {공격력} 만큼의 대미지를 준다.
 */
@Component
public class C001_BasicAttack implements CardEffect {
    @Override public String id() { return "C001"; }

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