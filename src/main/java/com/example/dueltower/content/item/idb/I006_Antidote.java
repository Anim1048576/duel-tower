package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.content.status.sdb.S102_Stun;
import com.example.dueltower.content.status.sdb.S103_Pressure;
import com.example.dueltower.content.status.sdb.S105_Weak;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.content.status.sdb.S107_Confusion;
import com.example.dueltower.content.status.sdb.S108_Seal;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.PlayerState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class I006_Antidote implements ItemBlueprint {
    public static final String ID = ItemIds.ANTIDOTE;

    private static final List<String> CLEANSABLE_STATUS_IDS = List.of(
            S101_Pain.ID,
            S102_Stun.ID,
            S103_Pressure.ID,
            S105_Weak.ID,
            S106_Vulnerable.ID,
            S107_Confusion.ID,
            S108_Seal.ID
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
                "전투 중 사용 가능 · 아군 1명 해로운 상태 1개 해제",
                "아군 1명의 무작위 [해로운 상태] 1개를 해제합니다.",
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

        List<String> activeDebuffs = new ArrayList<>();
        for (String statusId : CLEANSABLE_STATUS_IDS) {
            if (ps.status(statusId) > 0) {
                activeDebuffs.add(statusId);
            }
        }
        if (activeDebuffs.isEmpty()) {
            return;
        }

        long mix = ctx.state().seed() ^ ctx.state().version() ^ targetPlayerId.value().hashCode();
        int index = Math.floorMod((int) mix, activeDebuffs.size());
        ps.statusSet(activeDebuffs.get(index), 0);
    }
}
