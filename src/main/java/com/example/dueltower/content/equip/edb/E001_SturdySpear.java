package com.example.dueltower.content.equip.edb;

import com.example.dueltower.content.equip.model.EquipBlueprint;
import com.example.dueltower.engine.model.EquipDefinition;
import com.example.dueltower.engine.model.EquipSlot;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class E001_SturdySpear implements EquipBlueprint {
    public static final String ID = EquipIds.STURDY_SPEAR;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public EquipDefinition definition() {
        return new EquipDefinition(
                id(),
                "튼튼한 죽창",
                EquipSlot.WEAPON,
                "[장비] 공격력 +2, 받는 피해 +1",
                "장착 시 전반적인 공격력이 증가하지만, 받는 피해가 1 증가합니다.",
                List.of("장비", "근접", "무기"),
                null
        );
    }
}
