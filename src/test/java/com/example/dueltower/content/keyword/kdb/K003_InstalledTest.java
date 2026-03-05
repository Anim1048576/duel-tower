package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.core.effect.keyword.MoveCtx;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class K003_InstalledTest {

    private final K003_Installed installed = new K003_Installed();

    @Test
    void redirectsPlayToFieldWhenDefaultDestinationIsGrave() {
        KeywordRuntime rt = new KeywordRuntime(K003_Installed.ID, 1);
        MoveCtx c = new MoveCtx(
                new PlayerState(new Ids.PlayerId("P1")),
                new Ids.CardInstId("I1"),
                Zone.HAND,
                Zone.GRAVE,
                MoveReason.PLAY
        );

        Zone next = installed.overrideMoveDestination(rt, c, Zone.GRAVE);

        assertEquals(Zone.FIELD, next);
    }

    @Test
    void keepsDestinationForNonPlayMoves() {
        KeywordRuntime rt = new KeywordRuntime(K003_Installed.ID, 1);
        MoveCtx c = new MoveCtx(
                new PlayerState(new Ids.PlayerId("P1")),
                new Ids.CardInstId("I1"),
                Zone.FIELD,
                Zone.GRAVE,
                MoveReason.DESTROY
        );

        Zone next = installed.overrideMoveDestination(rt, c, Zone.GRAVE);

        assertEquals(Zone.GRAVE, next);
    }
}
