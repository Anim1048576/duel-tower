package com.example.dueltower.content.card.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 플레이어 보유 카드 슬롯.
 * strengthened=true 는 카드가 강화된 상태를 의미한다.
 * weakened=true 는 카드가 약화된 상태를 의미한다.
 * lockedInDeck=true 는 현재 덱에서 제거할 수 없는 카드 슬롯을 의미한다.
 */
public record OwnedCard(
        String ownedCardId,
        String cardId,
        List<OwnedCardModifier> modifiers,
        boolean lockedInDeck
) {
    public static final String MODIFIER_STRENGTHENED = "STRENGTHENED";
    public static final String MODIFIER_WEAKENED = "WEAKENED";

    public OwnedCard {
        cardId = normalizeRequired(cardId, "cardId");
        ownedCardId = normalizeOwnedCardId(ownedCardId);
        modifiers = normalizeModifiers(modifiers);
    }

    public static OwnedCard fromLegacy(String cardId, boolean strengthened, boolean weakened, boolean lockedInDeck) {
        List<OwnedCardModifier> modifiers = new ArrayList<>();
        if (strengthened) {
            modifiers.add(new OwnedCardModifier(MODIFIER_STRENGTHENED, 1));
        }
        if (weakened) {
            modifiers.add(new OwnedCardModifier(MODIFIER_WEAKENED, 1));
        }
        return new OwnedCard(UUID.randomUUID().toString(), cardId, modifiers, lockedInDeck);
    }

    public boolean strengthened() {
        return hasModifier(MODIFIER_STRENGTHENED);
    }

    public boolean weakened() {
        return hasModifier(MODIFIER_WEAKENED);
    }

    public boolean hasModifier(String modifierId) {
        if (modifierId == null || modifierId.isBlank()) {
            return false;
        }
        String normalized = modifierId.trim();
        return modifiers.stream().anyMatch(modifier -> modifier.modifierId().equals(normalized));
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
        List<OwnedCardModifier> out = new ArrayList<>(source.size());
        for (OwnedCardModifier modifier : source) {
            if (modifier == null) {
                throw new IllegalArgumentException("modifiers contains null");
            }
            out.add(new OwnedCardModifier(modifier.modifierId(), modifier.value()));
        }
        return List.copyOf(out);
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
