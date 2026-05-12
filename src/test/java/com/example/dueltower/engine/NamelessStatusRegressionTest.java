package com.example.dueltower.engine;

import com.example.dueltower.content.status.sdb.player.nameless.Nameless201_Entropy;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.engine.core.effect.status.StatusPhases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NamelessStatusRegressionTest {

    @Test
    @DisplayName("Entropy heals below half HP")
    void entropyHealsBelowHalfHp() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(8);
        fx.player.statusSet(Nameless201_Entropy.ID, 3);

        turnEnd(fx);

        assertEquals(11, fx.player.hp(), "below half HP should heal by entropy stacks");
    }

    @Test
    @DisplayName("Entropy damages at or above half HP")
    void entropyDamagesAtOrAboveHalfHp() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless201_Entropy.ID, 3);

        turnEnd(fx);

        assertEquals(7, fx.player.hp(), "at half HP or above should take entropy damage");
    }

    @Test
    @DisplayName("Entropy increments after turn-end processing")
    void entropyIncrementsAfterProcessing() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(8);
        fx.player.statusSet(Nameless201_Entropy.ID, 1);

        turnEnd(fx);

        assertEquals(2, fx.player.status(Nameless201_Entropy.ID), "entropy should increment by 1 after resolving");
    }

    @Test
    @DisplayName("Entropy removes itself when it reaches 10")
    void entropyRemovesItselfWhenItReachesTen() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.player.hp(20);
        fx.player.statusSet(Nameless201_Entropy.ID, 9);

        turnEnd(fx);

        assertEquals(11, fx.player.hp(), "entropy stack 9 should still deal damage before removal");
        assertEquals(0, fx.player.status(Nameless201_Entropy.ID), "entropy should be removed when next value reaches 10");
        assertFalse(fx.player.statusValues().containsKey(Nameless201_Entropy.ID), "removed entropy should not remain in status map");
    }

    @Test
    @DisplayName("Event horizon blocks entropy healing and damage from changing HP")
    void eventHorizonBlocksEntropyHpChanges() {
        NamelessRegressionFixture healing = new NamelessRegressionFixture();
        healing.player.hp(8);
        healing.player.statusSet(Nameless202_EventHorizon.ID, 1);
        healing.player.statusSet(Nameless201_Entropy.ID, 3);

        turnEnd(healing);

        assertEquals(8, healing.player.hp(), "event horizon should block entropy healing");
        assertEquals(4, healing.player.status(Nameless201_Entropy.ID), "blocked entropy healing should still increment stacks");

        NamelessRegressionFixture damaging = new NamelessRegressionFixture();
        damaging.player.hp(10);
        damaging.player.statusSet(Nameless202_EventHorizon.ID, 1);
        damaging.player.statusSet(Nameless201_Entropy.ID, 3);

        turnEnd(damaging);

        assertEquals(10, damaging.player.hp(), "event horizon should block entropy damage");
        assertEquals(4, damaging.player.status(Nameless201_Entropy.ID), "blocked entropy damage should still increment stacks");
    }

    private static void turnEnd(NamelessRegressionFixture fx) {
        StatusPhases.turnEnd(fx.state, fx.ctx, fx.selfRef(), fx.events, "TEST");
    }
}
