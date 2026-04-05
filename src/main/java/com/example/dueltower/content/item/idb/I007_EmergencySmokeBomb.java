package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.content.status.sdb.S004_Evasion;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.PlayerState;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class I007_EmergencySmokeBomb implements ItemBlueprint {
    public static final String ID = ItemIds.EMERGENCY_SMOKE_BOMB;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "긴급 연막탄",
                true,
                "전투 중 사용 가능 · 사용자 [회피] 1",
                "사용자에게 [회피] 1을 부여합니다.",
                List.of("소모품", "회피")
        );
    }

    @Override
    public void resolveUse(UseItemResolutionContext ctx) {
        Ids.PlayerId targetPlayerId = (ctx.targetCharacterId() == null || ctx.targetCharacterId().isBlank())
                ? ctx.actor().playerId()
                : new Ids.PlayerId(ctx.targetCharacterId().trim());

        PlayerState ps = ctx.state().player(targetPlayerId);
        if (ps == null) {
            return;
        }

        ps.statusAdd(S004_Evasion.ID, ctx.useCount());
    }
}
