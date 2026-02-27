package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [이로운 상태 : 도발]
 * 상대는 적을 대상으로 할 때 자신을 대상으로 해야 한다.
 * 효과 적용시, 이 수치를 1 내린다.
 */
@Component
public class S005_Taunt implements StatusBlueprint {
    public static final String ID = "TAUNT";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "도발",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                50,
                false,
                """
                        상대는 적을 대상으로 할 때 자신을 대상으로 해야 한다.
                        효과 적용시, 이 수치를 1 내린다.
                        """
        );
    }

    @Override
    public void validateEnemyOneTarget(
            StatusRuntime rt,
            TargetRef actor,
            Ids.CardInstId cardId,
            TargetRef chosenEnemy,
            List<TargetRef> enemyCandidates,
            List<String> errors
    ) {
        if (!(chosenEnemy instanceof TargetRef.Enemy)) return;

        List<TargetRef> taunts = tauntHolders(rt, enemyCandidates);
        if (taunts.isEmpty()) return;

        if (!taunts.contains(chosenEnemy)) {
            errors.add("taunt: must target one of " + taunts.stream().map(CombatState::actorKey).toList());
        }
    }

    @Override
    public TargetRef onResolveEnemyOneTarget(
            StatusRuntime rt,
            TargetRef actor,
            Ids.CardInstId cardId,
            TargetRef chosenEnemy,
            List<TargetRef> candidates
    ) {
        if (!(chosenEnemy instanceof TargetRef.Enemy)) return chosenEnemy;

        List<TargetRef> taunts = tauntHolders(rt, candidates);
        if (taunts.isEmpty()) return chosenEnemy;

        TargetRef finalTarget = taunts.contains(chosenEnemy) ? chosenEnemy : taunts.get(0);

        // Consume 1 stack from the chosen taunt holder
        int cur = rt.stacks(finalTarget, id());
        if (cur > 0) rt.stacksSet(finalTarget, id(), cur - 1);

        rt.log("TAUNT forces target: " + CombatState.actorKey(finalTarget) + " (remaining=" + (cur - 1) + ")");
        return finalTarget;
    }

    private List<TargetRef> tauntHolders(StatusRuntime rt, List<TargetRef> candidates) {
        List<TargetRef> r = new ArrayList<>();
        if (candidates == null) return r;
        for (TargetRef t : candidates) {
            int stacks = rt.stacks(t, id());
            if (stacks > 0) r.add(t);
        }
        return r;
    }
}
