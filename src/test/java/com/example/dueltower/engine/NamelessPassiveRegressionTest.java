package com.example.dueltower.engine;

import com.example.dueltower.content.passive.pdb.player.nameless.Nameless001_Passive;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless002_Passive;
import com.example.dueltower.content.status.sdb.S005_Taunt;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.engine.core.effect.EffectOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamelessPassiveRegressionTest {

    @Test
    @DisplayName("Particle emission adds 1 to skill-card status application")
    void particleEmissionAddsOneToSkillCardStatusApplication() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless001_Passive.ID, Nameless002_Passive.ID);
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addHandCard(NamelessRegressionFixture.TEST_SKILL_ID), fx.selfTarget()))
                .addStatus(com.example.dueltower.engine.model.Target.SELF, S106_Vulnerable.ID, 3);

        assertEquals(4, fx.player.status(S106_Vulnerable.ID), "skill-card status amount should include particle emission +1");
    }

    @Test
    @DisplayName("Particle emission does not add 1 to EX status application")
    void particleEmissionDoesNotAddOneToExStatusApplication() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless001_Passive.ID, Nameless002_Passive.ID);
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addHandCard(NamelessRegressionFixture.TEST_EX_ID), fx.selfTarget()))
                .addStatus(com.example.dueltower.engine.model.Target.SELF, S005_Taunt.ID, 7);

        assertEquals(7, fx.player.status(S005_Taunt.ID), "EX status amount must not receive particle emission +1");
    }

    @Test
    @DisplayName("Particle resonance heals by actual applied status amount divided by 3")
    void particleResonanceHealsByActualAppliedAmountDividedByThree() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless001_Passive.ID, Nameless002_Passive.ID);
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addHandCard(NamelessRegressionFixture.TEST_SKILL_ID), fx.selfTarget()))
                .addStatus(com.example.dueltower.engine.model.Target.SELF, S106_Vulnerable.ID, 5);

        assertEquals(6, fx.player.status(S106_Vulnerable.ID), "actual applied amount should include emission before resonance");
        assertEquals(12, fx.player.hp(), "actual applied amount 6 should heal floor(6 / 3) = 2");
    }

    @Test
    @DisplayName("Particle resonance with 0 heal amount does not change HP")
    void particleResonanceWithZeroHealAmountDoesNotChangeHp() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless001_Passive.ID, Nameless002_Passive.ID);
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addHandCard(NamelessRegressionFixture.TEST_EX_ID), fx.selfTarget()))
                .addStatus(com.example.dueltower.engine.model.Target.SELF, S005_Taunt.ID, 2);

        assertEquals(2, fx.player.status(S005_Taunt.ID), "EX should apply exactly 2 taunt");
        assertEquals(10, fx.player.hp(), "floor(2 / 3) = 0 should not emit a heal");
    }

    @Test
    @DisplayName("Event horizon blocks particle resonance healing")
    void eventHorizonBlocksParticleResonanceHealing() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless001_Passive.ID, Nameless002_Passive.ID);
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        new EffectOps(fx.effectContext(fx.addHandCard(NamelessRegressionFixture.TEST_SKILL_ID), fx.selfTarget()))
                .addStatus(com.example.dueltower.engine.model.Target.SELF, S106_Vulnerable.ID, 3);

        assertEquals(4, fx.player.status(S106_Vulnerable.ID), "status application should still happen under event horizon");
        assertEquals(10, fx.player.hp(), "event horizon should block particle resonance healing");
    }
}
