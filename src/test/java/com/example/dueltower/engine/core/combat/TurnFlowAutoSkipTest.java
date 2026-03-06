package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.TargetRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TurnFlowAutoSkipTest {

    @Test
    void shouldAutoSkipTurn_isReleasedWhenBattleIncapacitatedIsCleared() throws Exception {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(0);
        state.players().put(playerId, player);

        Method shouldAutoSkipTurn = TurnFlow.class.getDeclaredMethod("shouldAutoSkipTurn", GameState.class, TargetRef.class);
        shouldAutoSkipTurn.setAccessible(true);

        player.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 1);
        boolean withIncapacitated = (boolean) shouldAutoSkipTurn.invoke(null, state, TargetRef.ofPlayer(playerId));

        player.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 0);
        boolean afterCleared = (boolean) shouldAutoSkipTurn.invoke(null, state, TargetRef.ofPlayer(playerId));

        assertThat(withIncapacitated).isTrue();
        assertThat(afterCleared).isFalse();
    }
}
