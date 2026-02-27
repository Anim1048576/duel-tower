package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

@Component
public class S002_Regeneration implements StatusBlueprint {
    public static final String ID = "REGEN";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(), "재생",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                100,
                false,
                """
                        턴 종료시, 해당 수치만큼 체력을 회복하고 이 수치를 절반 감소한다.
                        """
        );
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;

        if (owner instanceof TargetRef.Player p) {
            PlayerState ps = rt.state().player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());
            ps.hp(ps.hp() + stacks);
        } else if (owner instanceof TargetRef.Enemy e) {
            EnemyState es = rt.state().enemy(e.id());
            if (es == null) throw new IllegalStateException("missing enemy: " + e.id().value());
            es.hp(es.hp() + stacks);
        }

        rt.stacksSet(owner, id(), stacks / 2);
    }
}
