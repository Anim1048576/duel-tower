package com.example.dueltower.engine.model;

import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CardInstanceMetadataTest {

    @Test
    void constructorDefensivelyCopiesModifiersAndNormalizesBlankSourceOwnedCardId() {
        List<OwnedCardModifier> modifiers = new ArrayList<>();
        modifiers.add(new OwnedCardModifier("WEAKENED", 1));

        CardInstance ci = new CardInstance(
                new CardInstId(UUID.randomUUID()),
                new CardDefId("C001"),
                new PlayerId("p1"),
                Zone.DECK,
                "   ",
                modifiers
        );

        modifiers.add(new OwnedCardModifier("STRENGTHENED", 1));

        assertNull(ci.sourceOwnedCardId());
        assertEquals(1, ci.modifiers().size());
        assertTrue(ci.hasModifier("WEAKENED"));
        assertFalse(ci.hasModifier("STRENGTHENED"));
    }
}
