package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [이로운 상태 : 회피]
 * 적에게 대미지를 받을 때, 그것을 무시하고 대미지를 0으로 하고 이 수치를 1 내린다.
 */
@Component
public class S004_Evasion implements StatusBlueprint {
    public static final String ID = "EVASION";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "회피",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                1,
                false,
                """
                        적에게 대미지를 받을 때, 그것을 무시하고 대미지를 0으로 하고 이 수치를 1 내린다.
                        """
        );
    }

    @Override
    public int onIncomingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        int stacks = rt.stacks(owner, id());
        if (stacks <= 0 || amount <= 0) return amount;
        if (source == null) return amount;

        // Only when hit by an opponent.
        if (CombatState.factionOf(source) == CombatState.factionOf(target)) return amount;

        rt.stacksSet(owner, id(), stacks - 1);
        rt.log("EVASION negates damage to " + CombatState.actorKey(target) + " (remaining=" + (stacks - 1) + ")");
        return 0;
    }
}
