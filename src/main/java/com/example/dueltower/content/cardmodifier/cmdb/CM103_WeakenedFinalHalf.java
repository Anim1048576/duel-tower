package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierRuntime;
import com.example.dueltower.engine.core.effect.cardmodifier.OutgoingCardValueCtx;
import com.example.dueltower.engine.model.CardModifierDefinition;
import com.example.dueltower.engine.model.TargetRef;
import org.springframework.stereotype.Component;

@Component
public class CM103_WeakenedFinalHalf implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.WEAKENED_FINAL_HALF; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "약화: 최종 위력 반감", 400, "최종 주는 피해/회복량이 절반이 된다.");
    }

    @Override
    public int onOutgoingDamage(CardModifierRuntime rt, OutgoingCardValueCtx c, TargetRef target, int amount) {
        return Math.max(0, amount / 2);
    }

    @Override
    public int onOutgoingHeal(CardModifierRuntime rt, OutgoingCardValueCtx c, TargetRef target, int amount) {
        return Math.max(0, amount / 2);
    }
}
