package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.model.CardModifierDefinition;
import org.springframework.stereotype.Component;

@Component
public class CM001_StrengthenedMarker implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.STRENGTHENED; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "강화됨", 10, "마커 전용 modifier (현재 단계에서는 런타임 효과 없음)");
    }
}
