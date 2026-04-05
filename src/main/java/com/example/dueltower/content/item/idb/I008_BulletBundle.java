package com.example.dueltower.content.item.idb;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.engine.model.ItemDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class I008_BulletBundle implements ItemBlueprint {
    public static final String ID = ItemIds.BULLET_BUNDLE;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ItemDefinition definition() {
        return new ItemDefinition(
                id(),
                "탄환 묶음",
                false,
                "사용 불가 · 휴대용 권총과 연동될 예정인 예비 탄약",
                "현재는 직접 사용할 수 없는 아이템입니다.",
                List.of("기타", "탄약")
        );
    }
    @Override
    public void resolveUse(com.example.dueltower.engine.core.effect.item.UseItemResolutionContext ctx) {
        // battleUsable=false 아이템이므로 실제 사용 경로에 진입하지 않음
    }

}
