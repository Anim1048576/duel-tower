package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [이로운 상태 : 경화]
 * 적에게 대미지를 받을 경우, 이 수치만큼 받는 대미지가 감소한다.
 * 턴을 개시하면 모두 제거한다.
 */
@Component
public class S109_Hardening implements StatusBlueprint {
    public static final String ID = "HARDENING";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "경화",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                java.util.Set.of(),
                8,
                false,
                """
                        적에게 대미지를 받을 경우, 이 수치만큼 받는 대미지가 감소한다.
                        턴을 개시하면 모두 제거한다.
                        """
        );
    }

    @Override
    public int onIncomingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        int hardening = rt.stacks(owner, id());
        if (hardening <= 0 || amount <= 0) return amount;
        if (source == null) return amount;
        if (CombatState.factionOf(source) == CombatState.factionOf(target)) return amount;

        return Math.max(0, amount - hardening);
    }

    @Override
    public void onTurnStart(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;
        rt.stacksSet(owner, id(), 0);
    }
}
