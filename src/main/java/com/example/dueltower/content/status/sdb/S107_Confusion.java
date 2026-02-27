package com.example.dueltower.content.status.sdb;

import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * [해로운 상태 : 혼란]
 * 적을 대상으로 할 때 아군도 대상으로 할 수 있으며, [도발]을 무시하고 무작위로 대상을 지정해야 한다.
 */
@Component
public class S107_Confusion implements StatusBlueprint {
    public static final String ID = "CONFUSION";
    @Override public String id() { return ID; }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "혼란",
                StatusKind.DEBUFF,
                StatusScope.CHARACTER,
                30,
                false,
                """
                        적을 대상으로 할 때 아군도 대상으로 할 수 있으며, [도발]을 무시하고 무작위로 대상을 지정해야 한다.
                        """
        );
    }

    @Override
    public TargetRef onResolveEnemyOneTarget(
            StatusRuntime rt,
            TargetRef actor,
            Ids.CardInstId cardId,
            TargetRef chosenEnemy,
            List<TargetRef> candidates
    ) {
        int stacks = rt.stacks(actor, id());
        if (stacks <= 0) return chosenEnemy;
        if (candidates == null || candidates.isEmpty()) return chosenEnemy;

        long seed = rt.state().seed()
                ^ rt.state().version()
                ^ (long) actor.hashCode()
                ^ (long) cardId.value().hashCode()
                ^ ((long) stacks << 32);

        Random rnd = new Random(seed);
        TargetRef picked = candidates.get(rnd.nextInt(candidates.size()));

        rt.log("CONFUSION overrides target: " + CombatState.actorKey(picked));
        return picked;
    }
}
