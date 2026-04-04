package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.model.ItemDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class I005_EnhancementShard implements ItemBlueprint {
    public static final String ID = ItemIds.ENHANCEMENT_SHARD;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "강화석 파편",
                false,
                "강화 재료",
                "장비 강화 수치에 따라 다량으로 요구됩니다.",
                List.of("재료")
        );
    }

    @Override
    public void resolveUse(UseItemResolutionContext ctx) {
    }
}
