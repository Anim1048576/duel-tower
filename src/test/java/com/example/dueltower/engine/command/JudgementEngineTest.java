package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JudgementEngineTest {

    @Test
    void rollLessOrEqualThanAbilitySucceedsImmediately() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.body(8);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 8,
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        JudgementEngine.InitialResult initial = engine.resolveInitial(player, player.playerId(), "BODY", 7L, 2L);
        JudgementEngine.Result result = engine.finalizeResult(player, player.playerId(), initial, false, 7L, 2L);

        assertTrue(initial.initialSuccess());
        assertFalse(initial.memoryAcceptAllowed());
        assertTrue(result.finalSuccess());
        assertNull(result.increasedAbility());
        assertNull(result.increasedAbilityValue());
    }

    @Test
    void rollGreaterThanAbilityCreatesMemoryChoiceCandidate() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.skill(7);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 15,
                (pool, seed, version, playerId, abilityId) -> "WEAKENED_FINAL_HALF"
        );

        JudgementEngine.InitialResult initial = engine.resolveInitial(player, player.playerId(), "SKILL", 7L, 2L);

        assertFalse(initial.initialSuccess());
        assertTrue(initial.memoryAcceptAllowed());
        assertFalse(initial.naturalTwenty());
    }

    @Test
    void naturalTwentyFailureCannotAcceptMemory() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.will(19);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 20,
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        JudgementEngine.InitialResult initial = engine.resolveInitial(player, player.playerId(), "WILL", 7L, 2L);
        JudgementEngine.Result result = engine.finalizeResult(player, player.playerId(), initial, false, 7L, 2L);

        assertFalse(initial.initialSuccess());
        assertFalse(initial.memoryAcceptAllowed());
        assertTrue(initial.naturalTwenty());
        assertFalse(result.finalSuccess());
        assertEquals(20, result.increasedAbilityValue());
    }

    @Test
    void memoryAcceptIncreasesByCeilHalfDifferenceAndAddsWeakness() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.sense(7);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 12,
                (pool, seed, version, playerId, abilityId) -> "WEAKENED_RANDOM_ENEMY_ONE"
        );

        JudgementEngine.InitialResult initial = engine.resolveInitial(player, player.playerId(), "SENSE", 7L, 2L);
        JudgementEngine.Result result = engine.finalizeResult(player, player.playerId(), initial, true, 7L, 2L);

        assertTrue(result.finalSuccess());
        assertTrue(result.memoryAccepted());
        assertEquals(10, result.increasedAbilityValue()); // ceil((12-7)/2)=3
        assertEquals(1, player.status("judgement.weakness.WEAKENED_RANDOM_ENEMY_ONE"));
    }

    @Test
    void memoryRejectIncreasesByOneAndCapsAtTwenty() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.body(19);
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 19 + 1,
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        JudgementEngine.InitialResult initial = engine.resolveInitial(player, player.playerId(), "BODY", 7L, 2L);
        JudgementEngine.Result result = engine.finalizeResult(player, player.playerId(), initial, false, 7L, 2L);

        assertFalse(result.finalSuccess());
        assertEquals(20, result.increasedAbilityValue());
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
                () -> engine.resolveInitial(player, player.playerId(), "WILL", 7L, 2L));
        assertTrue(ex.getMessage().contains("ability already maxed"));
    }

    @Test
    void weaknessPickerIsDeterministicInTestDouble() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.body(1);
        AtomicReference<List<String>> observedPool = new AtomicReference<>();
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 5,
                (pool, seed, version, playerId, abilityId) -> {
                    observedPool.set(pool);
                    return "WEAKENED_RANDOM_ENEMY_ONE";
                }
        );

        JudgementEngine.InitialResult initial = engine.resolveInitial(player, player.playerId(), "BODY", 7L, 2L);
        JudgementEngine.Result result = engine.finalizeResult(player, player.playerId(), initial, true, 7L, 2L);
        assertTrue(result.memoryAccepted());
        assertTrue(observedPool.get().contains("WEAKENED_RANDOM_ENEMY_ONE"));
    }
}
