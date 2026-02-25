package com.example.dueltower.content.card.cdb;

import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기본 저주 [코스트:2]
 * 효과: 적 1명에게 {공격력} 만큼의 [고통]을 부여한다.
 */
@Component
public class C004_BasicCurse implements CardEffect {

    public static final String PAIN = "PAIN"; // [고통] 상태 키

    @Override
    public String id() { return "C004"; }

    @Override
    public List<String> validate(EffectContext ec) {
        return new EffectOps(ec).validateTarget(Target.ENEMY_ONE);
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);

        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());

        int pain = me.attackPower(); // 룰: 고통 수치 = 공격력
        ops.addStatus(Target.ENEMY_ONE, PAIN, pain);
    }
}