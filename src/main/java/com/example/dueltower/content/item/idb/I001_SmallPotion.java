package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.TargetRef;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class I001_SmallPotion implements ItemBlueprint {
    public static final String ID = ItemIds.SMALL_POTION;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "소형 회복 물약",
                true,
                "전투 중 사용 가능 · 체력 20 회복",
                "즉시 체력을 20 회복합니다. 턴 소모 없이 사용됩니다.",
                List.of("소모품", "회복")
        );
    }

    @Override
    public void resolveUse(UseItemResolutionContext ctx) {
        Ids.PlayerId targetId = (ctx.targetCharacterId() == null || ctx.targetCharacterId().isBlank())
                ? ctx.actor().playerId()
                : new Ids.PlayerId(ctx.targetCharacterId().trim());

        HealOps.apply(
                ctx.state(),
                ctx.ctx(),
                ctx.out(),
                "item:" + id(),
                TargetRef.ofPlayer(targetId),
                20 * ctx.useCount()
        );
    }
}
