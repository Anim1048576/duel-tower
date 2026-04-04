package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.model.ItemDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class I003_LeatherStrap implements ItemBlueprint {
    public static final String ID = ItemIds.LEATHER_STRAP;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "단단한 가죽끈",
                false,
                "제작 재료",
                "장비 제작에 사용되는 기본 재료입니다.",
                List.of("재료")
        );
    }

    @Override
    public void resolveUse(UseItemResolutionContext ctx) {
    }
}
