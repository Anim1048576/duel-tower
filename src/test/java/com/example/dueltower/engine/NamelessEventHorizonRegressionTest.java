package com.example.dueltower.engine;

import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.engine.command.EndTurnCommand;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.combat.DamageFlags;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.PendingDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamelessEventHorizonRegressionTest {

    @Test
    @DisplayName("Event horizon ignores normal damage")
    void eventHorizonIgnoresNormalDamage() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        DamageOps.apply(fx.state, fx.ctx, fx.events, fx.enemyRef(), "normal", fx.selfRef(), 5);

        assertEquals(10, fx.player.hp(), "normal damage should not change HP during event horizon");
    }

    @Test
    @DisplayName("Event horizon ignores normal healing")
    void eventHorizonIgnoresNormalHealing() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        HealOps.apply(fx.state, fx.ctx, fx.events, fx.selfRef(), "normal", fx.selfRef(), 5);

        assertEquals(10, fx.player.hp(), "normal healing should not change HP during event horizon");
    }

    @Test
    @DisplayName("Event horizon allows its own damage source")
    void eventHorizonAllowsItsOwnDamageSource() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        DamageOps.apply(
                fx.state,
                fx.ctx,
                fx.events,
                fx.selfRef(),
                null,
                "event horizon",
                Nameless202_EventHorizon.ID,
                fx.selfRef(),
                5,
                DamageFlags.NONE
        );

        assertEquals(5, fx.player.hp(), "event horizon source damage should pass through its prevention hook");
    }

    @Test
    @DisplayName("Event horizon limits skill cards to one per turn")
    void eventHorizonLimitsSkillCardsToOnePerTurn() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.cardsPlayedThisTurn(1);
        CardInstance skill = fx.state.card(fx.addHandCard(NamelessRegressionFixture.TEST_SKILL_ID));
        List<String> errors = new ArrayList<>();

        StatusOps.validatePlayCard(fx.state, fx.ctx, fx.selfRef(), skill, fx.ctx.def(NamelessRegressionFixture.TEST_SKILL_ID), errors);

        assertTrue(errors.stream().anyMatch(error -> error.contains("1")), "second skill card should be rejected, errors=" + errors);
    }

    @Test
    @DisplayName("Event horizon does not limit EX usage")
    void eventHorizonDoesNotLimitExUsage() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.cardsPlayedThisTurn(1);
        CardInstance ex = fx.state.card(fx.addCard(NamelessRegressionFixture.TEST_EX_ID, com.example.dueltower.engine.model.Zone.EX));
        List<String> errors = new ArrayList<>();

        StatusOps.validateUseEx(fx.state, fx.ctx, fx.selfRef(), ex, fx.ctx.def(NamelessRegressionFixture.TEST_EX_ID), errors);

        assertTrue(errors.isEmpty(), "EX usage should not be rejected by event horizon, errors=" + errors);
    }

    @Test
    @DisplayName("Event horizon creates a pending choice at turn end")
    void eventHorizonCreatesPendingChoiceAtTurnEnd() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new EndTurnCommand(UUID.randomUUID(), fx.state.version(), NamelessRegressionFixture.PLAYER_ID)
        );

        assertTrue(result.accepted(), "ending the turn should succeed");
        PendingDecision.EventHorizonChoice decision = assertInstanceOf(
                PendingDecision.EventHorizonChoice.class,
                fx.player.pendingDecision(),
                "event horizon should open its own pending decision"
        );
        assertEquals(Nameless202_EventHorizon.CHOICE_IDS, decision.choiceIds(), "pending choices should match event horizon choices");
        assertTrue(result.events().stream().anyMatch(event -> event instanceof GameEvent.PendingDecisionSet set
                && set.playerId().equals(NamelessRegressionFixture.PLAYER_ID.value())
                && set.type().equals(Nameless202_EventHorizon.DECISION_TYPE)), "result should include PendingDecisionSet event");
    }

    @Test
    @DisplayName("Event horizon TAKE_DAMAGE and REMOVE_STATUS choices resolve")
    void eventHorizonChoicesResolve() {
        NamelessRegressionFixture takeDamage = new NamelessRegressionFixture();
        takeDamage.player.hp(20);
        takeDamage.player.statusSet(Nameless202_EventHorizon.ID, 1);
        takeDamage.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                NamelessRegressionFixture.PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult damageResult = takeDamage.resolveEventHorizon(Nameless202_EventHorizon.CHOICE_TAKE_DAMAGE);

        assertTrue(damageResult.accepted(), "TAKE_DAMAGE choice should be accepted");
        assertEquals(12, takeDamage.player.hp(), "TAKE_DAMAGE should deal max HP 40% event horizon damage");
        assertEquals(1, takeDamage.player.status(Nameless202_EventHorizon.ID), "TAKE_DAMAGE should keep event horizon active");
        assertNull(takeDamage.player.pendingDecision(), "TAKE_DAMAGE should clear pending decision");

        NamelessRegressionFixture removeStatus = new NamelessRegressionFixture();
        removeStatus.player.statusSet(Nameless202_EventHorizon.ID, 1);
        removeStatus.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                NamelessRegressionFixture.PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult removeResult = removeStatus.resolveEventHorizon(Nameless202_EventHorizon.CHOICE_REMOVE_STATUS);

        assertTrue(removeResult.accepted(), "REMOVE_STATUS choice should be accepted");
        assertEquals(0, removeStatus.player.status(Nameless202_EventHorizon.ID), "REMOVE_STATUS should remove event horizon");
        assertNull(removeStatus.player.pendingDecision(), "REMOVE_STATUS should clear pending decision");
    }
}
