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
                "장착 가능한 근접 무기",
                "장착하면 WEAPON 슬롯을 점유합니다. 현재는 장착 상태만 관리합니다.",
                List.of("장비", "근접", "무기")
        );
    }
}
