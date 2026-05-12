package com.example.dueltower.engine;

import com.example.dueltower.content.card.cdb.player.nameless.Nameless901_EX;
import com.example.dueltower.content.status.sdb.S005_Taunt;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless201_Entropy;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless203_EventHorizonUsed;
import com.example.dueltower.engine.core.EngineResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamelessExRegressionTest {

    @Test
    @DisplayName("Nameless EX augment 1 toggles taunt")
    void augmentOneTogglesTaunt() {
        NamelessRegressionFixture apply = new NamelessRegressionFixture();
        apply.setNamelessEx();

        EngineResult applyResult = apply.useNamelessEx(apply.enemyTarget(), Nameless901_EX.AUGMENT_1);

        assertTrue(applyResult.accepted(), "augment 1 should apply taunt when missing");
        assertEquals(7, apply.enemy.status(S005_Taunt.ID), "augment 1 should apply taunt 7");

        NamelessRegressionFixture remove = new NamelessRegressionFixture();
        remove.enemy.statusSet(S005_Taunt.ID, 3);
        remove.setNamelessEx();

        EngineResult removeResult = remove.useNamelessEx(remove.enemyTarget(), Nameless901_EX.AUGMENT_1);

        assertTrue(removeResult.accepted(), "augment 1 should remove taunt when present");
        assertEquals(0, remove.enemy.status(S005_Taunt.ID), "augment 1 should clear existing taunt");
    }

    @Test
    @DisplayName("Nameless EX augment 2 toggles entropy")
    void augmentTwoTogglesEntropy() {
        NamelessRegressionFixture apply = new NamelessRegressionFixture();
        apply.setNamelessEx();

        EngineResult applyResult = apply.useNamelessEx(apply.enemyTarget(), Nameless901_EX.AUGMENT_2);

        assertTrue(applyResult.accepted(), "augment 2 should apply entropy when missing");
        assertEquals(4, apply.enemy.status(Nameless201_Entropy.ID), "augment 2 should apply entropy 4");

        NamelessRegressionFixture remove = new NamelessRegressionFixture();
        remove.enemy.statusSet(Nameless201_Entropy.ID, 4);
        remove.setNamelessEx();

        EngineResult removeResult = remove.useNamelessEx(remove.enemyTarget(), Nameless901_EX.AUGMENT_2);

        assertTrue(removeResult.accepted(), "augment 2 should remove entropy when present");
        assertEquals(0, remove.enemy.status(Nameless201_Entropy.ID), "augment 2 should clear existing entropy");
    }

    @Test
    @DisplayName("Nameless EX event horizon augment is self-target only")
    void eventHorizonAugmentIsSelfTargetOnly() {
        NamelessRegressionFixture self = new NamelessRegressionFixture();
        self.setNamelessEx();

        EngineResult selfResult = self.useNamelessEx(self.selfTarget(), Nameless901_EX.AUGMENT_3);

        assertTrue(selfResult.accepted(), "augment 3 should accept self target");
        assertEquals(1, self.player.status(Nameless202_EventHorizon.ID), "augment 3 should apply event horizon to self");

        NamelessRegressionFixture other = new NamelessRegressionFixture();
        other.setNamelessEx();

        EngineResult otherResult = other.useNamelessEx(other.otherTarget(), Nameless901_EX.AUGMENT_3);

        assertFalse(otherResult.accepted(), "augment 3 should reject non-self target");
        assertTrue(otherResult.errors().contains("AUGMENT_3 requires self target"), "wrong-target error should be explicit");
    }

    @Test
    @DisplayName("Nameless EX event horizon augment is limited to once per session")
    void eventHorizonAugmentIsLimitedToOncePerSession() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.statusSet(Nameless203_EventHorizonUsed.ID, 1);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.selfTarget(), Nameless901_EX.AUGMENT_3);

        assertFalse(result.accepted(), "augment 3 should reject when event horizon used marker exists");
        assertTrue(result.errors().contains("AUGMENT_3 already used"), "once-per-session error should be explicit");
    }

    @Test
    @DisplayName("Existing EX cards still work without choiceId")
    void existingExCardsStillWorkWithoutChoiceId() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setTigEx();
        fx.addDeckCard(NamelessRegressionFixture.FILLER_ID);

        EngineResult result = fx.useTigEx(fx.enemyTarget());

        assertTrue(result.accepted(), "existing EX without choiceId should still be accepted, errors=" + result.errors());
        assertEquals(26, fx.enemy.hp(), "Tig EX should keep its pre-choiceId behavior");
    }
}
