package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

@Component
public class S003_Vigor implements StatusBlueprint {
    public static final String ID = "VIGOR";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "활력",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                20,
                false,
                """
                        적에게 대미지를 줄 경우, 이 수치만큼 추가 대미지를 준다.
                        턴을 종료하면 모두 제거한다.
                        """
        );
    }

    @Override
    public int onOutgoingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        int stacks = rt.stacks(owner, id());
        if (stacks <= 0 || amount <= 0) return amount;
        if (source == null) return amount;

        if (CombatState.factionOf(source) == CombatState.factionOf(target)) return amount;
        return amount + stacks;
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;
        rt.stacksSet(owner, id(), 0);
    }
}
