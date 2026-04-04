package com.example.dueltower.session.runtime;

import com.example.dueltower.engine.command.StartCombatCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;
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
        StateMapper.configureItemDefsForTest(defaultItemDefs());
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

        assertEquals("소형 회복 물약", item.name());
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
        assertTrue(i4.bound());
    }

    @Test
    void toDtoFailsFastWhenItemDefinitionIsMissing() {
        StateMapper.configureItemDefsForTest(Map.of());
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 321L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StateMapper.toDto("ABCD1234", state));

        assertTrue(ex.getMessage().contains("item definition not found"));
    }

    private static Map<String, ItemDefinition> defaultItemDefs() {
        return Map.of(
                "I-1", new ItemDefinition("I-1", "소형 회복 물약", true, "전투 중 사용 가능 · 체력 20 회복", "즉시 체력을 20 회복합니다. 턴 소모 없이 사용됩니다.", List.of("소모품", "회복")),
                "I-2", new ItemDefinition("I-2", "해독제", true, "전투 중 사용 가능 · 디버프 해제", "출혈/중독 등 해로운 상태효과 1개를 제거합니다.", List.of("소모품", "정화")),
                "I-3", new ItemDefinition("I-3", "단단한 가죽끈", false, "제작 재료", "장비 제작에 사용되는 기본 재료입니다.", List.of("재료")),
                "I-4", new ItemDefinition("I-4", "긴급 연막탄", true, "전투 중 사용 가능 · 회피 상승", "현재 턴 동안 회피율이 크게 상승합니다.", List.of("전투 아이템")),
                "I-5", new ItemDefinition("I-5", "강화석 파편", false, "강화 재료", "장비 강화 수치에 따라 다량으로 요구됩니다.", List.of("재료"))
        );
    }
}
