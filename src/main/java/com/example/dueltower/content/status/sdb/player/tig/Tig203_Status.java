package com.example.dueltower.content.status.sdb.player.tig;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.content.status.sdb.S004_Evasion;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/** 다음 자신의 턴 시작 시, 이 상태의 수치만큼 부여했던 회피를 제거한다. */
@Component
public class Tig203_Status implements StatusBlueprint {
    public static final String ID = "Tig203_Status";

    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "위기 극복 지속시간",
                StatusKind.NEUTRAL,
                StatusScope.CHARACTER,
                java.util.Set.of(),
                26,
                true,
                "다음 자신의 턴 개시 시 회피를 제거한다."
        );
    }

    @Override
    public void onTurnStart(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;
        int evasion = rt.stacks(owner, S004_Evasion.ID);
        if (evasion > 0) rt.stacksSet(owner, S004_Evasion.ID, Math.max(0, evasion - stacks));
        rt.stacksSet(owner, id(), 0);
    }
}
