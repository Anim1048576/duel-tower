package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.PlayerId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTest {

    @Test
    void defaultLifeStatsStartAtOne() {
        PlayerState ps = new PlayerState(new PlayerId("P1"));

        assertEquals(1, ps.body());
        assertEquals(1, ps.skill());
        assertEquals(1, ps.sense());
        assertEquals(1, ps.will());
    }

    @Test
    void nonPositiveLifeStatsAreClampedToOne() {
        PlayerState ps = new PlayerState(new PlayerId("P1"));

        ps.body(0);
        ps.skill(-1);
        ps.sense(0);
        ps.will(-99);

        assertEquals(1, ps.body());
        assertEquals(1, ps.skill());
        assertEquals(1, ps.sense());
        assertEquals(1, ps.will());
    }

    @Test
    void positiveLifeStatsAreKept() {
        PlayerState ps = new PlayerState(new PlayerId("P1"));

        ps.body(3);
        ps.skill(4);
        ps.sense(5);
        ps.will(6);

        assertEquals(3, ps.body());
        assertEquals(4, ps.skill());
        assertEquals(5, ps.sense());
        assertEquals(6, ps.will());
    }

    @Test
    void defaultPlayerHasPositiveAttackAndHealPower() {
        PlayerState ps = new PlayerState(new PlayerId("P1"));

        assertTrue(ps.attackPower() > 0);
        assertTrue(ps.healPower() > 0);
    }
}
