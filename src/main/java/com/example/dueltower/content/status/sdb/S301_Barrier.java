package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

@Component
public class S301_Barrier implements StatusBlueprint {
    public static final String ID = "BARRIER";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "방벽",
                StatusKind.BUFF,
                StatusScope.FACTION,
                9,
                false,
                """
                        【진영 효과】
                        아군 진영이 적에게 대미지를 받을 때, 그것을 먼저 n만큼 흡수하고 남은 피해만 전달한다.
                        """
        );
    }

    @Override
    public int onIncomingDamage(StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
        if (!(owner instanceof StatusOwnerRef.Faction)) return amount;

        int barrier = rt.stacks(owner, id());
        if (barrier <= 0 || amount <= 0) return amount;

        if (source == null) return amount;
        if (CombatState.factionOf(source) == CombatState.factionOf(target)) return amount;

        int absorbed = Math.min(barrier, amount);
        rt.stacksSet(owner, id(), barrier - absorbed);
        return amount - absorbed;
    }
}
