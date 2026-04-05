package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.EquipRef;
import com.example.dueltower.engine.model.ItemRef;
import com.example.dueltower.engine.model.RunState;

import java.util.List;

public final class RunConfigs {

    private static final RunConfig DEFAULT = new RunConfig(
            2,
            1,
            12450,
            List.of(
                    RunState.InventoryEntry.item(new ItemRef("I-1"), 3, false),
                    RunState.InventoryEntry.item(new ItemRef("I-2"), 1, false),
                    RunState.InventoryEntry.item(new ItemRef("I-4"), 1, false),
                    RunState.InventoryEntry.item(new ItemRef("I-6"), 1, false)
            ),
            List.of(
                    new RunConfig.RunNodeDefinition("N-1", "회랑 정찰", "판정", "판정 성공 시 안전한 지름길 발견", RunState.NodePhase.JUDGEMENT, RunState.Danger.MID, false, null),
                    new RunConfig.RunNodeDefinition("N-2", "붕괴 전장", "전투", "적 선공 확률 증가", RunState.NodePhase.COMBAT, RunState.Danger.HIGH, false, null),
                    new RunConfig.RunNodeDefinition("N-3", "폐허 저장고", "이벤트", "보상 카드 1장 획득", RunState.NodePhase.EVENT, RunState.Danger.LOW, false, null),
                    new RunConfig.RunNodeDefinition("N-4", "봉인된 균열", "전투", "열쇠 미보유 시 입장 불가", RunState.NodePhase.COMBAT, RunState.Danger.HIGH, true, "균열 열쇠가 없어 진입할 수 없음"),
                    new RunConfig.RunNodeDefinition("N-5", "안식처", "이벤트", "체력과 행동력을 정비한다", RunState.NodePhase.EVENT, RunState.Danger.LOW, false, null)
            ),
            List.of(
                    new RunState.ShopOffer("O-1", new ItemRef("I-1"), 50, 5, false),
                    new RunState.ShopOffer("O-2", new ItemRef("I-2"), 200, 5, false),
                    new RunState.ShopOffer("O-3", new ItemRef("I-3"), 500, 5, false),
                    new RunState.ShopOffer("O-4", new ItemRef("I-4"), 50, 5, false),
                    new RunState.ShopOffer("O-5", new ItemRef("I-5"), 200, 5, false),
                    new RunState.ShopOffer("O-6", new ItemRef("I-6"), 250, 5, false),
                    new RunState.ShopOffer("O-7", new ItemRef("I-7"), 500, 5, false),
                    new RunState.ShopOffer("O-8", new EquipRef("E-1"), 200, 5, false),
                    new RunState.ShopOffer("O-9", new EquipRef("E-2"), 250, 5, false),
                    new RunState.ShopOffer("O-10", new ItemRef("I-8"), 25, 5, false)
            )
    );

    private RunConfigs() {
    }

    public static RunConfig defaultConfig() {
        return DEFAULT;
    }
}
