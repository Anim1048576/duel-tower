package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierRuntime;
import com.example.dueltower.engine.core.effect.cardmodifier.PlayCardModifierCtx;
import com.example.dueltower.engine.model.CardModifierDefinition;
import org.springframework.stereotype.Component;

@Component
public class CM102_WeakenedSelfDamage10 implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.WEAKENED_SELF_DAMAGE_10; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "약화: 반동", 210, "카드 사용 시 효과 해결 전에 자신이 10 피해를 받는다.");
    }

    @Override
    public void beforeResolvePlayCard(CardModifierRuntime rt, PlayCardModifierCtx c) {
        int amount = 10 * Math.max(1, rt.value());
        DamageOps.apply(rt.state(), rt.ctx(), rt.out(), c.actor(), c.def().id().value() + "::" + id(), c.actor(), amount);
    }
}
