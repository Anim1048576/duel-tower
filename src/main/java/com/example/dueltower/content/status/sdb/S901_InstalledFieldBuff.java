package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

@Component
public class S901_InstalledFieldBuff implements StatusBlueprint {
    public static final String ID = "INSTALLED_FIELD_BUFF";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "설치 버프",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                java.util.Set.of(),
                5,
                false,
                "필드에 남아 있는 설치 카드 수만큼 주는 피해가 증가한다."
        );
    }

    @Override
    public int onOutgoingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        int stacks = rt.stacks(owner, id());
        if (stacks <= 0 || source == null || amount <= 0) return amount;
        if (CombatState.factionOf(source) == CombatState.factionOf(target)) return amount;
        return amount + stacks;
    }
}
