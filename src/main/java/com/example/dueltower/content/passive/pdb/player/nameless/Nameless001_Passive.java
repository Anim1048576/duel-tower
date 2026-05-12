package com.example.dueltower.content.passive.pdb.player.nameless;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.passive.PassiveRuntime;
import com.example.dueltower.engine.core.effect.status.StatusApplyContext;
import com.example.dueltower.engine.core.effect.status.StatusApplyResult;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.engine.model.StatusVisibility;
import com.example.dueltower.engine.model.TargetRef;
import org.springframework.stereotype.Component;

@Component
public class Nameless001_Passive implements PassiveBlueprint {
    public static final String ID = "Nameless001_Passive";

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
                "입자 공명",
                100,
                "자신이 상태를 부여했을 때, 실제 부여량의 1/3 만큼 자신의 체력을 회복한다."
        );
    }

    @Override
    public void onAfterApplyStatus(PassiveRuntime rt, StatusApplyContext apply, StatusApplyResult result) {
        if (!(apply.source() instanceof TargetRef.Player)) return;
        if (rt.ctx().hasStatusDef(apply.statusId())
                && rt.ctx().statusDef(apply.statusId()).visibility() == StatusVisibility.IMPLEMENTATION) {
            return;
        }
        if (result.actualAppliedAmount() <= 0) return;

        int heal = result.actualAppliedAmount() / 3;
        if (heal <= 0) return;

        HealOps.apply(
                rt.state(),
                rt.ctx(),
                rt.out(),
                apply.source(),
                "입자 공명",
                apply.source(),
                heal
        );
    }
}
