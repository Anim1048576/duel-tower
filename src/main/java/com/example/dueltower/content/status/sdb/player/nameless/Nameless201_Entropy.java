package com.example.dueltower.content.status.sdb.player.nameless;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.combat.CombatEntityOps;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class Nameless201_Entropy implements StatusBlueprint {
    public static final String ID = "ENTROPY";
    private static final int LIMIT = 10;
    private static final String SOURCE_LABEL = "엔트로피";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.NAMELESS;
    }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "엔트로피",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                Set.of(),
                50,
                false,
                """
                        턴 종료시 자신의 체력이 절반 미만이면 이 수치만큼 회복하고, 절반 이상이면 이 수치만큼 피해를 받는다.
                        이후 수치가 1 증가하며, 상한치 10에 도달하면 제거된다.
                        """
        );
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;

        int hp = CombatEntityOps.hp(rt.state(), owner);
        int maxHp = CombatEntityOps.maxHp(rt.state(), owner);
        if (hp * 2 < maxHp) {
            HealOps.apply(rt.state(), rt.ctx(), rt.out(), owner, SOURCE_LABEL, owner, stacks);
        } else {
            DamageOps.apply(rt.state(), rt.ctx(), rt.out(), owner, SOURCE_LABEL, owner, stacks);
        }

        int next = stacks + 1;
        rt.stacksSet(owner, id(), next >= LIMIT ? 0 : next);
    }
}
