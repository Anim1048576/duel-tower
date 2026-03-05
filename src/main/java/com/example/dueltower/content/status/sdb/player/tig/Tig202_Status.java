package com.example.dueltower.content.status.sdb.player.tig;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/** 오버 드라이브 설치 중 공격 보너스. */
@Component
public class Tig202_Status implements StatusBlueprint {
    public static final String ID = "Tig202_Status";

    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "오버드라이브 공격 강화",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                java.util.Set.of(),
                25,
                true,
                "오버 드라이브 설치로 얻는 공격 증가 효과."
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
