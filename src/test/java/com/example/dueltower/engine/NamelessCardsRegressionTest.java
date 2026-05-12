package com.example.dueltower.engine;

import com.example.dueltower.content.passive.pdb.player.nameless.Nameless002_Passive;
import com.example.dueltower.content.status.sdb.S001_Shield;
import com.example.dueltower.content.status.sdb.S002_Regeneration;
import com.example.dueltower.content.status.sdb.S003_Vigor;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.content.status.sdb.S104_Destruction;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamelessCardsRegressionTest {

    @Test
    @DisplayName("Space branches between ally healing and enemy pain")
    void spaceBranchesBetweenAllyAndEnemy() {
        NamelessRegressionFixture ally = new NamelessRegressionFixture();
        ally.player.hp(10);

        ally.resolve(ally.space, ally.selfTarget());

        assertEquals(12, ally.player.hp(), "Space should heal an ally by heal power");

        NamelessRegressionFixture enemy = new NamelessRegressionFixture();
        enemy.setPassives(Nameless002_Passive.ID);

        enemy.resolve(enemy.space, enemy.enemyTarget());

        assertEquals(2, enemy.enemy.status(S101_Pain.ID), "Space should apply attack/2 pain plus particle emission to an enemy");
    }

    @Test
    @DisplayName("Universe branches between ally regeneration and enemy damage")
    void universeBranchesBetweenAllyAndEnemy() {
        NamelessRegressionFixture ally = new NamelessRegressionFixture();
        ally.setPassives(Nameless002_Passive.ID);

        ally.resolve(ally.universe, ally.selfTarget());

        assertEquals(2, ally.player.status(S002_Regeneration.ID), "Universe should apply heal/2 regeneration plus particle emission to an ally");

        NamelessRegressionFixture enemy = new NamelessRegressionFixture();

        enemy.resolve(enemy.universe, enemy.enemyTarget());

        assertEquals(28, enemy.enemy.hp(), "Universe should damage an enemy by attack power");
    }

    @Test
    @DisplayName("Cosmos branches between ally shield and enemy destruction")
    void cosmosBranchesBetweenAllyAndEnemy() {
        NamelessRegressionFixture ally = new NamelessRegressionFixture();
        ally.setPassives(Nameless002_Passive.ID);

        ally.resolve(ally.cosmos, ally.selfTarget());

        assertEquals(5, ally.player.status(S001_Shield.ID), "Cosmos should apply heal*2 shield plus particle emission to an ally");

        NamelessRegressionFixture enemy = new NamelessRegressionFixture();
        enemy.setPassives(Nameless002_Passive.ID);

        enemy.resolve(enemy.cosmos, enemy.enemyTarget());

        assertEquals(2, enemy.enemy.status(S104_Destruction.ID), "Cosmos should apply attack/2 destruction plus particle emission to an enemy");
    }

    @Test
    @DisplayName("Chaos branches between ally healing and enemy damage")
    void chaosBranchesBetweenAllyAndEnemy() {
        NamelessRegressionFixture ally = new NamelessRegressionFixture();
        ally.player.hp(10);

        ally.resolve(ally.chaos, ally.selfTarget());

        assertEquals(13, ally.player.hp(), "Chaos should heal an ally by heal power + attack/2");

        NamelessRegressionFixture enemy = new NamelessRegressionFixture();

        enemy.resolve(enemy.chaos, enemy.enemyTarget());

        assertEquals(27, enemy.enemy.hp(), "Chaos should damage an enemy by attack power + heal/2");
    }

    @Test
    @DisplayName("Void deals damage, applies vulnerable, and discards one card")
    void voidDealsDamageAppliesVulnerableAndDiscards() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless002_Passive.ID);
        Ids.CardInstId discard = fx.addHandCard(NamelessRegressionFixture.FILLER_ID);

        fx.resolve(fx.voidCard, fx.enemyTarget(), List.of(discard));

        assertEquals(26, fx.enemy.hp(), "Void should deal attack + heal damage");
        assertEquals(4, fx.enemy.status(S106_Vulnerable.ID), "Void should apply vulnerable 3 plus particle emission");
        assertEquals(Zone.GRAVE, fx.state.card(discard).zone(), "discarded card should move to grave");
        assertTrue(fx.player.grave().contains(discard), "discarded card should be in grave list");
        assertFalse(fx.player.hand().contains(discard), "discarded card should leave hand");
    }

    @Test
    @DisplayName("Empty heals, applies vigor, and discards one card")
    void emptyHealsAppliesVigorAndDiscards() {
        NamelessRegressionFixture fx = new NamelessRegressionFixture();
        fx.setPassives(Nameless002_Passive.ID);
        fx.player.hp(10);
        Ids.CardInstId discard = fx.addHandCard(NamelessRegressionFixture.FILLER_ID);

        fx.resolve(fx.empty, fx.selfTarget(), List.of(discard));

        assertEquals(14, fx.player.hp(), "Empty should heal attack + heal");
        assertEquals(4, fx.player.status(S003_Vigor.ID), "Empty should apply vigor 3 plus particle emission");
        assertEquals(Zone.GRAVE, fx.state.card(discard).zone(), "discarded card should move to grave");
        assertTrue(fx.player.grave().contains(discard), "discarded card should be in grave list");
        assertFalse(fx.player.hand().contains(discard), "discarded card should leave hand");
    }
}
