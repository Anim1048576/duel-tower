package com.example.dueltower.session.runtime;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.RunState;
import com.example.dueltower.session.dto.SessionStateDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StateMapperRunStateTest {

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
}
