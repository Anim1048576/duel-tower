package com.example.dueltower.content.equip.edb;

import com.example.dueltower.content.equip.model.EquipBlueprint;
import com.example.dueltower.engine.model.EquipDefinition;
import com.example.dueltower.engine.model.EquipSlot;
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
                "장착 가능한 원거리 무기",
                "장착하면 WEAPON 슬롯을 점유합니다. 탄약/사격/재장전 규칙은 아직 구현되지 않았습니다.",
                List.of("장비", "원거리", "무기")
        );
    }
}
