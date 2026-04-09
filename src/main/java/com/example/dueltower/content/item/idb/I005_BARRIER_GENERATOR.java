package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.content.status.sdb.S301_Barrier;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.ItemDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class I005_BARRIER_GENERATOR implements ItemBlueprint {
    public static final String ID = ItemIds.BARRIER_GENERATOR;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "장벽 생성기",
                true,
                "전투 중 사용 가능 · 아군 진영 [방벽] 20",
                "아군 진영에 [방벽] 20을 적용합니다.",
                List.of("소모품", "방어")
        );
    }

    @Override
    public void resolveUse(UseItemResolutionContext ctx) {
        if (ctx.combat() == null) {
            return;
        }

        int added = 20 * ctx.useCount();
        ctx.combat().factionStatusValues(CombatState.FactionId.PLAYERS)
                .merge(S301_Barrier.ID, added, Integer::sum);

        ctx.out().add(new GameEvent.LogAppended("item:" + id() + " used (+BARRIER " + added + ")"));
    }
}
