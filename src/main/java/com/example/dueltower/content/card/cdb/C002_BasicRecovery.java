package com.example.dueltower.content.card.cdb;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class C002_BasicRecovery implements CardEffect {

    @Override
    public String id() { return "C002"; }

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