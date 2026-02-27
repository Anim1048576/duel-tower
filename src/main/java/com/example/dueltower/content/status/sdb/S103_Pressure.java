package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [해로운 상태 : 중압]
 * 지불해야하는 코스트가 수치만큼 증가한다.
 * 턴을 종료하면 모두 제거한다.
 */
@Component
public class S103_Pressure implements StatusBlueprint {
    public static final String ID = "PRESSURE";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "중압",
                StatusKind.DEBUFF,
                StatusScope.CHARACTER,
                100,
                false,
                """
                        지불해야하는 코스트가 수치만큼 증가한다.
                        턴을 종료하면 모두 제거한다.
                        """
        );
    }

    @Override
    public int onCost(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, int currentCost) {
        int stacks = rt.stacks(actor, id());
        if (stacks <= 0) return currentCost;
        return currentCost + stacks;
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;

        int next = 0;
        rt.stacksSet(owner, id(), next);
    }
}
