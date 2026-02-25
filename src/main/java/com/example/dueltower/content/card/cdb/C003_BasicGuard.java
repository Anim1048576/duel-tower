package com.example.dueltower.content.card.cdb;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

/**
 * 기본 방어 [코스트:1]
 * 효과: 자신은 {치유력} 만큼의 [보호]를 얻는다.
 */
@Component
public class C003_BasicGuard implements CardEffect {

    @Override
    public String id() { return "C003"; }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);

        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());

        int shield = me.healPower();
        ops.addStatus(Target.SELF, EffectOps.SHIELD, shield);
    }
}