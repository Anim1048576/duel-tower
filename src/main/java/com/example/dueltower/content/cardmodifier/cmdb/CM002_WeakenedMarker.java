package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.model.CardModifierDefinition;
import org.springframework.stereotype.Component;

@Component
public class CM002_WeakenedMarker implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.WEAKENED; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "약화됨", 11, "레거시 마커 modifier (현재 단계에서는 런타임 효과 없음)");
    }
}
