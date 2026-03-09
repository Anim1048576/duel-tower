package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierRuntime;
import com.example.dueltower.engine.core.effect.cardmodifier.ModifyCostCtx;
import com.example.dueltower.engine.model.CardModifierDefinition;
import org.springframework.stereotype.Component;

@Component
public class CM101_WeakenedCostPlusOne implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.WEAKENED_COST_PLUS_ONE; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "약화: 코스트 증가", 100, "카드 사용 코스트가 +1 된다.");
    }

    @Override
    public int modifyCost(CardModifierRuntime rt, ModifyCostCtx c, int currentCost) {
        return currentCost + Math.max(1, rt.value());
    }
}
