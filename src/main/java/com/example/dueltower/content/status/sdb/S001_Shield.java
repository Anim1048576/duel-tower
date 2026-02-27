package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [이로운 상태 : 보호]
 * 적에게 대미지를 받을 때, 그것을 먼저 n만큼 흡수하고 남은 피해만 받는다.
 */
@Component
public class S001_Shield implements StatusBlueprint {
    public static final String ID = "SHIELD";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "보호",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                10,
                false,
                """
                        적에게 대미지를 받을 때, 그것을 먼저 n만큼 흡수하고 남은 피해만 받는다.
                        """
        );
    }

    @Override
    public int onIncomingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        int shield = rt.stacks(owner, id());
        if (shield <= 0 || amount <= 0) return amount;

        int absorbed = Math.min(shield, amount);
        rt.stacksSet(owner, id(), shield - absorbed);
        return amount - absorbed;
    }
}
