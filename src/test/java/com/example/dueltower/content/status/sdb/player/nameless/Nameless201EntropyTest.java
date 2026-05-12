package com.example.dueltower.content.status.sdb.player.nameless;

import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.core.effect.status.StatusPhases;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class Nameless201EntropyTest {
    private static final Ids.PlayerId PLAYER_ID = new Ids.PlayerId("nameless");

    @Autowired
    private StatusService statusService;

    @Test
    @DisplayName("엔트로피는 StatusService에 자동 등록된다")
    void entropyStatusIsRegisteredByStatusService() {
        assertEquals(Nameless201_Entropy.ID, statusService.get(Nameless201_Entropy.ID).id());
        assertTrue(statusService.defsMap().containsKey(Nameless201_Entropy.ID));
        assertTrue(statusService.effectsMap().containsKey(Nameless201_Entropy.ID));
        assertTrue(statusService.list().stream().anyMatch(def -> def.id().equals(Nameless201_Entropy.ID)));
    }

    @Test
    @DisplayName("HP가 최대 HP의 절반 미만이면 턴 종료 시 엔트로피 수치만큼 회복하고 수치가 1 증가한다")
    void entropyHealsBelowHalfHpAndIncrements() {
        Fixture fx = new Fixture();
        fx.player.hp(8);
        fx.player.statusSet(Nameless201_Entropy.ID, 3);

        fx.turnEnd();

        assertEquals(11, fx.player.hp());
        assertEquals(4, fx.player.status(Nameless201_Entropy.ID));
        assertTrue(fx.hasEntropyCombatLog());
    }

    @Test
    @DisplayName("HP가 최대 HP의 절반 이상이면 턴 종료 시 엔트로피 수치만큼 피해를 받고 수치가 1 증가한다")
    void entropyDamagesAtOrAboveHalfHpAndIncrements() {
        Fixture fx = new Fixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless201_Entropy.ID, 3);

        fx.turnEnd();

        assertEquals(7, fx.player.hp());
        assertEquals(4, fx.player.status(Nameless201_Entropy.ID));
        assertTrue(fx.hasEntropyCombatLog());
    }

    @Test
    @DisplayName("엔트로피 수치가 9이면 턴 종료 처리 후 제거되고 10 이상으로 남지 않는다")
    void entropyRemovesAtLimit() {
        Fixture fx = new Fixture();
        fx.player.hp(20);
        fx.player.statusSet(Nameless201_Entropy.ID, 9);

        fx.turnEnd();

        assertEquals(11, fx.player.hp());
        assertEquals(0, fx.player.status(Nameless201_Entropy.ID));
        assertFalse(fx.player.statusValues().containsKey(Nameless201_Entropy.ID));
        assertTrue(fx.hasEntropyCombatLog());
    }

    private static final class Fixture {
        final Nameless201_Entropy entropy = new Nameless201_Entropy();
        final GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 201L);
        final PlayerState player = new PlayerState(PLAYER_ID);
        final List<GameEvent> events = new ArrayList<>();
        final EngineContext ctx;

        Fixture() {
            state.players().put(PLAYER_ID, player);
            ctx = new EngineContext(
                    Map.of(),
                    Map.of(),
                    Map.of(Nameless201_Entropy.ID, entropy.definition()),
                    Map.<String, StatusEffect>of(Nameless201_Entropy.ID, entropy)
            );
        }

        void turnEnd() {
            StatusPhases.turnEnd(state, ctx, TargetRef.ofPlayer(PLAYER_ID), events, "TURN_END");
        }

        boolean hasEntropyCombatLog() {
            return events.stream().anyMatch(event ->
                    event instanceof GameEvent.CombatLogAppended log
                            && "엔트로피".equals(log.actorName())
                            && "엔트로피".equals(log.cardName())
            );
        }
    }
}
