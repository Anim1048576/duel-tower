package com.example.dueltower.content.passive.pdb.player.nameless;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.core.effect.passive.PassiveRuntime;
import com.example.dueltower.engine.core.effect.status.StatusApplyContext;
import com.example.dueltower.engine.core.effect.status.StatusApplySourceKind;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.engine.model.TargetRef;
import org.springframework.stereotype.Component;

@Component
public class Nameless002_Passive implements PassiveBlueprint {
    public static final String ID = "Nameless002_Passive";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.NAMELESS;
    }

    @Override
    public PassiveDefinition definition() {
        return new PassiveDefinition(
                id(),
                "입자 방출",
                100,
                "자신이 스킬 카드로 부여하는 상태의 수치가 1 증가한다."
        );
    }

    @Override
    public int onBeforeApplyStatus(PassiveRuntime rt, StatusApplyContext apply, int currentAmount) {
        if (!(apply.source() instanceof TargetRef.Player)) return currentAmount;
        if (apply.sourceKind() != StatusApplySourceKind.CARD) return currentAmount;
        if (apply.sourceCardDef() == null || apply.sourceCardDef().type() != CardType.SKILL) return currentAmount;
        if (currentAmount <= 0) return currentAmount;
        return currentAmount + 1;
    }
}
