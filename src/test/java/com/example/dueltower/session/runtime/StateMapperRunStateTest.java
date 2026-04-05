package com.example.dueltower.session.runtime;

import com.example.dueltower.engine.command.StartCombatCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.session.dto.SessionStateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StateMapperRunStateTest {

    @BeforeEach
    void setUp() {
        StateMapper.configureDefsForTest(defaultItemDefs(), defaultEquipDefs());
    }

    @Test
    void toDtoIncludesRunStateSnapshot() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 777L);

        SessionStateDto dto = StateMapper.toDto("ABCD1234", state);

        assertNotNull(dto.run());
        assertEquals(1, dto.run().floor());
        assertEquals("CHOOSE_NODE", dto.run().status());
        assertFalse(dto.run().resultPending());
        assertNull(dto.run().currentNode());
        assertNotNull(dto.run().inventory());
        assertEquals(2, dto.run().inventory().keys());
        assertEquals(1, dto.run().inventory().chests());
        assertEquals(12450, dto.run().inventory().gold());
        assertEquals(3, dto.run().availableChoices().size());
    }

    @Test
    void toDtoMapsCurrentNodeAndRecentResultsAfterSelection() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 888L);

        RunState.NodeChoice selected = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() != RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();
        state.runState().beginNode(selected);
        state.runState().resolveCurrentNode("reward", "보상 획득", selected.name() + " 결과 확인", "테스트", 200, 0, 0);

        SessionStateDto dto = StateMapper.toDto("ABCD1234", state);

        assertNotNull(dto.run());
        assertEquals("SHOW_RESULTS", dto.run().status());
        assertTrue(dto.run().resultPending());
        assertNotNull(dto.run().currentNode());
        assertEquals(selected.id(), dto.run().currentNode().id());
        assertFalse(dto.run().recentResults().isEmpty());
    }

    @Test
    void toDtoIncludesCombatEnemiesAndKeepsRunResolveStateOnCombatStart() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 999L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        RunState.NodeChoice combatChoice = state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .filter(choice -> choice.phase() == RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        state.runState().beginNode(combatChoice);
        state.nodeState(NodeState.COMBAT);

        GameEngine engine = new GameEngine();
        assertTrue(engine.process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new StartCombatCommand(UUID.randomUUID(), state.version(), playerId)
        ).accepted());

        SessionStateDto dto = StateMapper.toDto("ABCD1234", state);

        assertNotNull(dto.combat());
        assertFalse(dto.combat().enemies().isEmpty(), "frontend-facing combat state should expose enemies");
        assertEquals("RESOLVE_NODE", dto.run().status());
        assertFalse(dto.run().resultPending());
    }

    @Test
    void toDtoComposesInventoryRuntimeWithItemDefinitions() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);

        SessionStateDto dto = StateMapper.toDto("ABCD1234", state);

        var item = dto.run().inventory().items().stream()
                .filter(i -> "I-1".equals(i.id()))
                .findFirst()
                .orElseThrow();

        assertEquals("ITEM", item.entryType());
        assertEquals("염가형 회복물약", item.name());
        assertEquals(3, item.count());
        assertFalse(item.bound());
        assertTrue(item.battleUsable());
        assertFalse(item.summary().isBlank());
        assertFalse(item.description().isBlank());
        assertFalse(item.tags().isEmpty());

        var i4 = dto.run().inventory().items().stream()
                .filter(i -> "I-4".equals(i.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, i4.count());
        assertFalse(i4.bound());
    }


    @Test
    void toDtoMapsEquipInventoryEntryWithEntryType() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1234L);
        var next = new java.util.ArrayList<>(state.runState().inventory().items());
        next.add(RunState.InventoryEntry.equip(UUID.randomUUID().toString(), new EquipRef("E-1"), false, null, null));
        state.runState().inventory().replaceItems(next);

        SessionStateDto dto = StateMapper.toDto("ABCD1234", state);

        var equip = dto.run().inventory().items().stream()
                .filter(i -> "E-1".equals(i.id()))
                .findFirst()
                .orElseThrow();

        assertEquals("EQUIP", equip.entryType());
        assertEquals("튼튼한 죽창", equip.name());
        assertFalse(equip.battleUsable());
        assertNotNull(equip.inventoryEquipId());
    }

    @Test
    void toDtoFailsFastWhenItemDefinitionIsMissing() {
        StateMapper.configureDefsForTest(Map.of(), defaultEquipDefs());
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 321L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StateMapper.toDto("ABCD1234", state));

        assertTrue(ex.getMessage().contains("item definition not found"));
    }

    private static Map<String, EquipDefinition> defaultEquipDefs() {
        return Map.of(
                "E-1", new EquipDefinition("E-1", "튼튼한 죽창", EquipSlot.WEAPON, "장착 가능한 근접 무기", "장착 가능한 근접 무기", List.of("장비", "근접", "무기"), null, null, null, null),
                "E-2", new EquipDefinition("E-2", "휴대용 권총", EquipSlot.WEAPON, "장착 가능한 원거리 무기", "장착 가능한 원거리 무기", List.of("장비", "원거리", "무기"), new EquipAmmoPolicy(6, 6), null, null, null)
        );
    }

    private static Map<String, ItemDefinition> defaultItemDefs() {
        return Map.of(
                "I-1", new ItemDefinition("I-1", "염가형 회복물약", true, "전투 중 사용 가능 · 아군 1명 체력 10 회복", "아군 1명의 체력을 10 회복합니다.", List.of("소모품", "회복")),
                "I-2", new ItemDefinition("I-2", "회복물약", true, "전투 중 사용 가능 · 아군 1명 체력 30 회복", "아군 1명의 체력을 30 회복합니다.", List.of("소모품", "회복")),
                "I-3", new ItemDefinition("I-3", "상급 회복물약", true, "전투 중 사용 가능 · 아군 1명 체력 50 회복", "아군 1명의 체력을 50 회복합니다.", List.of("소모품", "회복")),
                "I-4", new ItemDefinition("I-4", "염가형 장벽 생성기", true, "전투 중 사용 가능 · 아군 진영 [방벽] 8", "아군 진영에 [방벽] 8을 적용합니다.", List.of("소모품", "방어")),
                "I-5", new ItemDefinition("I-5", "장벽 생성기", true, "전투 중 사용 가능 · 아군 진영 [방벽] 20", "아군 진영에 [방벽] 20을 적용합니다.", List.of("소모품", "방어")),
                "I-6", new ItemDefinition("I-6", "해독제", true, "전투 중 사용 가능 · 아군 1명 해로운 상태 1개 해제", "아군 1명의 무작위 [해로운 상태] 1개를 해제합니다.", List.of("소모품", "정화")),
                "I-7", new ItemDefinition("I-7", "긴급 연막탄", true, "전투 중 사용 가능 · 사용자 [회피] 1", "사용자에게 [회피] 1을 부여합니다.", List.of("소모품", "회피")),
                "I-8", new ItemDefinition("I-8", "탄환 묶음", false, "사용 불가 · 휴대용 권총과 연동될 예정인 예비 탄약", "현재는 직접 사용할 수 없는 아이템입니다.", List.of("기타", "탄약"))
        );
    }
}
