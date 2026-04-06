package com.example.dueltower.engine.command;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgementEngineTest {

    @Test
    void rollLessOrEqualThanAbilitySucceeds() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.body(8);
        player.ownedCards(List.of(new OwnedCard("oc-1", "C001", List.of())));
        player.deckOwnedCardIds(List.of("oc-1"));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 8,
                (pool, seed, version, playerId, abilityId) -> pool.get(0),
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
        player.ownedCards(List.of(
                new OwnedCard("oc-1", "C001", List.of()),
                new OwnedCard("oc-2", "C002", List.of())
        ));
        player.deckOwnedCardIds(List.of("oc-1", "oc-2"));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 15,
                (pool, seed, version, playerId, abilityId) -> "WEAKENED_FINAL_HALF",
                (pool, seed, version, playerId, abilityId) -> "oc-2"
        );

        JudgementEngine.Result result = engine.resolve(player, player.playerId(), "SKILL", 7L, 2L);

        assertFalse(result.success());
        assertTrue(result.memoryAccepted());
        assertEquals("WEAKENED_FINAL_HALF", result.grantedWeakness());
        assertEquals("oc-2", result.targetOwnedCardId());
        assertTrue(player.ownedCards().stream()
                .filter(ownedCard -> "oc-2".equals(ownedCard.ownedCardId()))
                .findFirst()
                .orElseThrow()
                .hasModifier("WEAKENED_FINAL_HALF"));
        assertEquals(0, player.status("judgement.weakness.WEAKENED_FINAL_HALF"));
        assertEquals(8, player.skill());
    }

    @Test
    void failureFromNineteenRaisesAbilityToTwenty() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.sense(19);
        player.ownedCards(List.of(new OwnedCard("oc-1", "C001", List.of())));
        player.deckOwnedCardIds(List.of("oc-1"));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 20,
                (pool, seed, version, playerId, abilityId) -> pool.get(0),
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        engine.resolve(player, player.playerId(), "SENSE", 7L, 2L);
        assertEquals(20, player.sense());
    }

    @Test
    void judgementCannotRunWhenAbilityAlreadyTwenty() {
        PlayerState player = new PlayerState(new Ids.PlayerId("p1"));
        player.will(20);
        player.ownedCards(List.of(new OwnedCard("oc-1", "C001", List.of())));
        player.deckOwnedCardIds(List.of("oc-1"));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 1,
                (pool, seed, version, playerId, abilityId) -> pool.get(0),
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
        player.ownedCards(List.of(new OwnedCard("oc-1", "C001", List.of())));
        player.deckOwnedCardIds(List.of("oc-1"));
        AtomicReference<List<String>> observedPool = new AtomicReference<>();
        JudgementEngine engine = new JudgementEngine(
                (seed, version, playerId, abilityId) -> 20,
                (pool, seed, version, playerId, abilityId) -> {
                    observedPool.set(pool);
                    return "WEAKENED_RANDOM_ENEMY_ONE";
                },
                (pool, seed, version, playerId, abilityId) -> pool.get(0)
        );

        JudgementEngine.Result result = engine.resolve(player, player.playerId(), "BODY", 7L, 2L);
        assertEquals("WEAKENED_RANDOM_ENEMY_ONE", result.grantedWeakness());
        assertTrue(observedPool.get().contains("WEAKENED_RANDOM_ENEMY_ONE"));
    }

    @Test
    void weaknessSyncsAllInstancesWithSameOwnedCardIdOnly() {
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 7L);
        PlayerState player = new PlayerState(playerId);
        player.will(0);
        player.ownedCards(List.of(
                new OwnedCard("oc-target", "C001", List.of()),
                new OwnedCard("oc-other", "C002", List.of())
        ));
        player.deckOwnedCardIds(List.of("oc-target", "oc-other"));
        state.players().put(playerId, player);

        Ids.CardInstId targetDeck = Ids.newCardInstId();
        Ids.CardInstId targetHand = Ids.newCardInstId();
        Ids.CardInstId otherInst = Ids.newCardInstId();
        state.cardInstances().put(targetDeck, new com.example.dueltower.engine.model.CardInstance(
                targetDeck, new Ids.CardDefId("C001"), playerId, Zone.DECK, "oc-target", List.of()
        ));
        state.cardInstances().put(targetHand, new com.example.dueltower.engine.model.CardInstance(
                targetHand, new Ids.CardDefId("C001"), playerId, Zone.HAND, "oc-target", List.of()
        ));
        state.cardInstances().put(otherInst, new com.example.dueltower.engine.model.CardInstance(
                otherInst, new Ids.CardDefId("C002"), playerId, Zone.FIELD, "oc-other", List.of()
        ));

        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 20,
                (pool, seed, version, pid, abilityId) -> "WEAKENED_SELF_DAMAGE_10",
                (pool, seed, version, pid, abilityId) -> "oc-target"
        );
        JudgementEngine.Result result = engine.resolve(player, state, playerId, "WILL", 7L, 2L);

        assertEquals("oc-target", result.targetOwnedCardId());
        assertEquals(2, result.syncedInstanceCount());
        assertTrue(state.card(targetDeck).hasModifier("WEAKENED_SELF_DAMAGE_10"));
        assertTrue(state.card(targetHand).hasModifier("WEAKENED_SELF_DAMAGE_10"));
        assertFalse(state.card(otherInst).hasModifier("WEAKENED_SELF_DAMAGE_10"));
    }
}
