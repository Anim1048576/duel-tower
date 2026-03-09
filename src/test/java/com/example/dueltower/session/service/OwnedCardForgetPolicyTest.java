package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OwnedCardForgetPolicyTest {

    @Test
    void genericWeakenedMarkerBlocksForget() {
        OwnedCard card = new OwnedCard("oc-1", "C001", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED, 1)));

        OwnedCardForgetPolicy.ForgetCheck check = OwnedCardForgetPolicy.evaluateWithDeckMembership(card, Map.of("C001", 2), Map.of("C001", 0), false);

        assertFalse(check.forgettable());
        assertEquals("cannot forget weakened card", check.reason());
    }

    @Test
    void concreteWeakenedMarkerBlocksForget() {
        OwnedCard card = new OwnedCard("oc-1", "C001", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_SELF_DAMAGE_10, 1)));

        OwnedCardForgetPolicy.ForgetCheck check = OwnedCardForgetPolicy.evaluateWithDeckMembership(card, Map.of("C001", 2), Map.of("C001", 0), false);

        assertFalse(check.forgettable());
        assertEquals("cannot forget weakened card", check.reason());
    }

    @Test
    void strengthenedMarkerBlocksForget() {
        OwnedCard card = new OwnedCard("oc-1", "C001", List.of(new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1)));

        OwnedCardForgetPolicy.ForgetCheck check = OwnedCardForgetPolicy.evaluateWithDeckMembership(card, Map.of("C001", 2), Map.of("C001", 0), false);

        assertFalse(check.forgettable());
        assertEquals("cannot forget strengthened card", check.reason());
    }

    @Test
    void nonDeckCopyCanStillBeForgotten() {
        OwnedCard card = new OwnedCard("oc-2", "C001", List.of());

        OwnedCardForgetPolicy.ForgetCheck check = OwnedCardForgetPolicy.evaluateWithDeckMembership(card, Map.of("C001", 2), Map.of("C001", 1), false);

        assertTrue(check.forgettable());
        assertNull(check.reason());
    }
}
