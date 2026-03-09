package com.example.dueltower.content.cardmodifier.cmdb;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierRuntime;
import com.example.dueltower.engine.core.effect.cardmodifier.EnemyOneModifierTargetCtx;
import com.example.dueltower.engine.model.CardModifierDefinition;
import com.example.dueltower.engine.model.TargetRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class CM104_WeakenedRandomEnemyOne implements CardModifierBlueprint {
    @Override
    public String id() { return CardModifierIds.WEAKENED_RANDOM_ENEMY_ONE; }

    @Override
    public CardModifierDefinition definition() {
        return new CardModifierDefinition(id(), "약화: 타겟 혼선", 500, "ENEMY_ONE 타겟이 유효한 대상 중 무작위로 변경된다.");
    }

    @Override
    public TargetRef resolveEnemyOneTarget(CardModifierRuntime rt, EnemyOneModifierTargetCtx c, TargetRef chosenEnemy, List<TargetRef> candidates) {
        if (candidates == null || candidates.isEmpty()) return chosenEnemy;

        long mix = rt.state().seed();
        mix ^= (rt.state().version() * 0x9E3779B97F4A7C15L);
        mix ^= ((long) rt.out().size() << 32);
        if (c.cardId() != null) mix ^= c.cardId().value().hashCode();
        mix ^= c.actor().toString().hashCode();
        mix ^= id().hashCode();
        mix ^= chosenEnemy.toString().hashCode();

        Random rnd = new Random(mix);
        return candidates.get(rnd.nextInt(candidates.size()));
    }
}
