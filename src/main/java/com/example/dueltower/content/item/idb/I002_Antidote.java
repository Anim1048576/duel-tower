package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.event.GameEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class I002_Antidote implements ItemBlueprint {
    public static final String ID = ItemIds.ANTIDOTE;

    private static final Set<String> CLEANSABLE_STATUS_IDS = Set.of(
            "S101", // Pain
            "S102", // Stun
            "S103", // Pressure
            "S105", // Weak
            "S106", // Vulnerable
            "S107", // Confusion
            "S108"  // Seal
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "해독제",
                true,
                "전투 중 사용 가능 · 디버프 해제",
                "출혈/중독 등 해로운 상태효과 1개를 제거합니다.",
                List.of("소모품", "정화")
        );
    }

    @Override
    public void resolveUse(UseItemResolutionContext ctx) {
        if (ctx.targetCharacterId() == null || ctx.targetCharacterId().isBlank()) {
            return;
        }

        Ids.PlayerId targetPlayerId = new Ids.PlayerId(ctx.targetCharacterId().trim());
        PlayerState ps = ctx.state().player(targetPlayerId);
        if (ps == null) {
            return;
        }

        boolean removed = false;
        for (String statusId : CLEANSABLE_STATUS_IDS) {
            if (ps.status(statusId) > 0) {
                ps.statusSet(statusId, 0);
                removed = true;
                break;
            }
        }

        String outcome = removed ? "debuff removed" : "no debuff";
        ctx.out().add(new GameEvent.LogAppended("item:" + id() + " used on " + targetPlayerId.value() + " (" + outcome + ")"));
    }
}
