package com.example.dueltower.content.card.model;

import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 플레이어 보유 카드 슬롯.
 * 강화/약화/덱 고정 의미는 modifiers 기반으로 해석한다.
 */
public record OwnedCard(
        String ownedCardId,
        String cardId,
        List<OwnedCardModifier> modifiers
) {
    public OwnedCard {
        cardId = normalizeRequired(cardId, "cardId");
        ownedCardId = normalizeOwnedCardId(ownedCardId);
        modifiers = normalizeModifiers(modifiers);
    }

    public static OwnedCard fromLegacy(String cardId, boolean strengthened, boolean weakened, boolean lockedInDeck) {
        List<OwnedCardModifier> modifiers = new ArrayList<>();
        if (strengthened) {
            modifiers.add(new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1));
        }
        if (weakened) {
            modifiers.add(new OwnedCardModifier(CardModifierIds.WEAKENED, 1));
        }
        if (lockedInDeck) {
            modifiers.add(new OwnedCardModifier(CardModifierIds.LOCKED_IN_DECK, 1));
        }
        return new OwnedCard(UUID.randomUUID().toString(), cardId, modifiers);
    }

    public boolean strengthened() {
        return OwnedCardModifierSemantics.isStrengthened(modifiers);
    }

    public boolean weakened() {
        return OwnedCardModifierSemantics.isWeakened(modifiers);
    }

    public boolean lockedInDeck() {
        return OwnedCardModifierSemantics.isLockedInDeck(modifiers);
    }

    public boolean hasModifier(String modifierId) {
        return OwnedCardModifierSemantics.hasModifier(modifiers, modifierId);
    }

    public OwnedCard withLockInDeck(boolean locked) {
        if (locked) {
            return withAddedModifier(CardModifierIds.LOCKED_IN_DECK);
        }
        return withRemovedModifier(CardModifierIds.LOCKED_IN_DECK);
    }

    public OwnedCard withAddedModifier(String modifierId) {
        String normalized = normalizeRequired(modifierId, "modifierId");
        if (hasModifier(normalized)) {
            return this;
        }
        List<OwnedCardModifier> next = new ArrayList<>(modifiers);
        next.add(new OwnedCardModifier(normalized, 1));
        return new OwnedCard(ownedCardId, cardId, next);
    }

    public OwnedCard withRemovedModifier(String modifierId) {
        if (modifierId == null || modifierId.isBlank()) {
            return this;
        }
        String normalized = modifierId.trim();
        List<OwnedCardModifier> next = modifiers.stream()
                .filter(modifier -> !normalized.equals(modifier.modifierId()))
                .toList();
        if (next.size() == modifiers.size()) {
            return this;
        }
        return new OwnedCard(ownedCardId, cardId, next);
    }

    private static String normalizeOwnedCardId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return normalizeRequired(value, "ownedCardId");
    }

    private static List<OwnedCardModifier> normalizeModifiers(List<OwnedCardModifier> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Map<String, OwnedCardModifier> deduped = new LinkedHashMap<>();
        for (OwnedCardModifier modifier : source) {
            if (modifier == null) {
                throw new IllegalArgumentException("modifiers contains null");
            }
            String modifierId = modifier.modifierId();
            deduped.putIfAbsent(modifierId, new OwnedCardModifier(modifierId, modifier.value()));
        }
        return List.copyOf(deduped.values());
    }

    private static String normalizeRequired(String raw, String fieldName) {
        Objects.requireNonNull(raw, fieldName + " is required");
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is blank");
        }
        return normalized;
    }
}
