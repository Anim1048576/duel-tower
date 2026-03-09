package com.example.dueltower.content.card.model;

import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OwnedCardSemanticsTest {

    @Test
    void lockedInDeckDerivedFromModifierMarker() {
        OwnedCard locked = new OwnedCard("oc-1", "C001", List.of(new OwnedCardModifier(CardModifierIds.LOCKED_IN_DECK, 1)));
        OwnedCard unlocked = locked.withLockInDeck(false);

        assertTrue(locked.lockedInDeck());
        assertFalse(unlocked.lockedInDeck());
    }

    @Test
    void weakenedIncludesConcreteWeakenedModifier() {
        OwnedCard card = new OwnedCard("oc-1", "C001", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_COST_PLUS_ONE, 1)));

        assertTrue(card.weakened());
        assertFalse(card.hasModifier(CardModifierIds.WEAKENED));
    }

    @Test
    void duplicateModifiersAreNormalized() {
        OwnedCard card = new OwnedCard("oc-1", "C001", List.of(
                new OwnedCardModifier("  " + CardModifierIds.STRENGTHENED + " ", 1),
                new OwnedCardModifier(CardModifierIds.STRENGTHENED, 5)
        ));

        assertEquals(1, card.modifiers().size());
        assertTrue(card.strengthened());
    }
}
