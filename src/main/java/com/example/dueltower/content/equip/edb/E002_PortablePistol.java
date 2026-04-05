package com.example.dueltower.content.equip.edb;

import com.example.dueltower.content.equip.model.EquipBlueprint;
import com.example.dueltower.content.item.idb.ItemIds;
import com.example.dueltower.engine.model.EquipActionDefinition;
import com.example.dueltower.engine.model.EquipAmmoPolicy;
import com.example.dueltower.engine.model.EquipDefinition;
import com.example.dueltower.engine.model.EquipReloadPolicy;
import com.example.dueltower.engine.model.EquipSlot;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class E002_PortablePistol implements EquipBlueprint {
    public static final String ID = EquipIds.PORTABLE_PISTOL;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public EquipDefinition definition() {
        return new EquipDefinition(
                id(),
                "휴대용 권총",
                EquipSlot.WEAPON,
                "[장비 액션] 장전 탄환 1 소모 · 적 1명에게 12 피해",
                "기본 장전량 6/6. 장비 액션으로 사격하고, 탄환 묶음(I-8)으로 재장전할 수 있습니다.",
                List.of("장비", "원거리", "무기", "장비액션"),
                new EquipAmmoPolicy(6, 6),
                new EquipReloadPolicy(ItemIds.BULLET_BUNDLE, 6),
                new EquipActionDefinition(
                        "E-2-FIRE",
                        "사격",
                        "장전 탄환 1 소모 · 적 1명에게 12 피해",
                        "장전된 탄환을 1 소모해 적 1명에게 고정 12 피해를 줍니다.",
                        Target.ENEMY_ONE,
                        0,
                        true,
                        1,
                        12
                ),
                null
        );
    }
}
