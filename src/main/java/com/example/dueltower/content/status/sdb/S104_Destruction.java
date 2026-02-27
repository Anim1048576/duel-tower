package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

/**
 * [해로운 상태 : 파괴]
 * [스킬 카드]를 낼 경우, 이 수치만큼 대미지를 받는다.
 * 턴을 종료하면 모두 제거한다.
 */
@Component
public class S104_Destruction implements StatusBlueprint {
    public static final String ID = "DESTRUCTION";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "파괴",
                StatusKind.DEBUFF,
                StatusScope.CHARACTER,
                100,
                false,
                """
                        [스킬 카드]를 낼 경우, 이 수치만큼 대미지를 받는다.
                        턴을 종료하면 모두 제거한다.
                        """
        );
    }

    @Override
    public void onAfterPlayCard(StatusRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def) {
        int stacks = rt.stacks(actor, id());
        if (stacks <= 0) return;
        if (def.type() != CardType.SKILL) return;

        // self-damage (do not go through DamageOps; this is "recoil" from the rule text)
        if (actor instanceof TargetRef.Player p) {
            PlayerState ps = rt.state().player(p.id());
            if (ps == null) return;
            ps.hp(ps.hp() - stacks);
            rt.log("DESTRUCTION deals " + stacks + " to " + CombatState.actorKey(actor) + " (hp=" + ps.hp() + "/" + ps.maxHp() + ")");
            return;
        }

        if (actor instanceof TargetRef.Enemy e) {
            EnemyState es = rt.state().enemy(e.id());
            if (es == null) return;
            es.hp(es.hp() - stacks);
            rt.log("DESTRUCTION deals " + stacks + " to " + CombatState.actorKey(actor) + " (hp=" + es.hp() + "/" + es.maxHp() + ")");
        }
    }

    @Override
    public void onTurnEnd(StatusRuntime rt, TargetRef owner, int stacks) {
        if (stacks <= 0) return;

        int next = 0;
        rt.stacksSet(owner, id(), next);
    }
}
