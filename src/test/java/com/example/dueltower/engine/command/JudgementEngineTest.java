package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgementEngineTest {

    @Test
    void rollLessOrEqualThanAbilitySucceeds() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.body(8);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 8,
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        JudgementEngine.Result result = engine.resolve(player, player.playerId(), "BODY", 7L, 2L);

        assertTrue(result.success());
        assertFalse(result.memoryAccepted());
        assertEquals(8, result.roll());
        assertEquals(8, result.abilityBefore());
        assertEquals(8, player.body());
    }

    @Test
    void rollGreaterThanAbilityFailsAndAcceptsMemory() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.skill(7);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 15,
                (pool, seed, version, playerId, abilityId) -> "WEAKENED_FINAL_HALF"
        );

        JudgementEngine.Result result = engine.resolve(player, player.playerId(), "SKILL", 7L, 2L);

        assertFalse(result.success());
        assertTrue(result.memoryAccepted());
        assertEquals("WEAKENED_FINAL_HALF", result.grantedWeakness());
        assertEquals(1, player.status("judgement.weakness.WEAKENED_FINAL_HALF"));
        assertEquals(8, player.skill());
    }

    @Test
    void failureFromNineteenRaisesAbilityToTwenty() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.sense(19);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 20,
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        engine.resolve(player, player.playerId(), "SENSE", 7L, 2L);
        assertEquals(20, player.sense());
    }

    @Test
    void judgementCannotRunWhenAbilityAlreadyTwenty() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.will(20);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 1,
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.resolve(player, player.playerId(), "WILL", 7L, 2L));
        assertTrue(ex.getMessage().contains("ability already maxed"));
    }

    @Test
    void weaknessPickerIsDeterministicInTestDouble() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.body(1);
        AtomicReference<List<String>> observedPool = new AtomicReference<>();
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 20,
                (pool, seed, version, playerId, abilityId) -> {
                    observedPool.set(pool);
                    return "WEAKENED_RANDOM_ENEMY_ONE";
                }
        );

        JudgementEngine.Result result = engine.resolve(player, player.playerId(), "BODY", 7L, 2L);
        assertEquals("WEAKENED_RANDOM_ENEMY_ONE", result.grantedWeakness());
        assertTrue(observedPool.get().contains("WEAKENED_RANDOM_ENEMY_ONE"));
    }
}
