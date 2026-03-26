package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.card.model.OwnedCardModifierSemantics;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 세션/덱 관련 정규화 허브.
 * <p>
 * Canonical runtime rules:
 * - ownedCards: {@link OwnedCard#ownedCardId()} 를 가진 사본 단위 목록
 * - currentSkillDeck: ownedCardId 선택 목록
 * <p>
 * persisted/legacy payload(예: cardId 배열)는 이 클래스를 통해 runtime canonical 형태로 변환한다.
 */
public final class SessionNormalizationSupport {

    private SessionNormalizationSupport() {
    }

    public static List<OwnedCard> normalizeOwnedCards(List<OwnedCardDto> ownedCardsRaw) {
        List<OwnedCard> out = new ArrayList<>(ownedCardsRaw.size());
        for (OwnedCardDto dto : ownedCardsRaw) {
            if (dto == null || dto.cardId() == null || dto.cardId().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "ownedCards.cardId is required");
            }
            String ownedCardId = (dto.ownedCardId() == null || dto.ownedCardId().isBlank())
                    ? UUID.randomUUID().toString()
                    : dto.ownedCardId().trim();
            List<OwnedCardModifier> modifiers = normalizeOwnedCardModifiers(dto);
            out.add(new OwnedCard(ownedCardId, dto.cardId().trim(), modifiers));
        }
        return List.copyOf(out);
    }

    public static List<OwnedCardModifier> normalizeOwnedCardModifiers(OwnedCardDto dto) {
        List<OwnedCardModifier> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (dto.modifiers() != null) {
            for (OwnedCardModifierDto modifierDto : dto.modifiers()) {
                if (modifierDto == null || modifierDto.modifierId() == null || modifierDto.modifierId().isBlank()) {
                    continue;
                }
                String modifierId = modifierDto.modifierId().trim();
                int value = modifierDto.value() == null ? 0 : modifierDto.value();
                if (!seen.add(modifierId + "\u0000" + value)) {
                    continue;
                }
                out.add(new OwnedCardModifier(modifierId, value));
            }
        }

        if (Boolean.TRUE.equals(dto.strengthened())
                && out.stream().noneMatch(m -> CardModifierIds.STRENGTHENED.equals(m.modifierId()))) {
            out.add(new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1));
        }
        if (Boolean.TRUE.equals(dto.weakened())
                && out.stream().noneMatch(m -> CardModifierIds.WEAKENED.equals(m.modifierId()))
                && !OwnedCardModifierSemantics.hasConcreteWeakenedModifier(out)) {
            out.add(new OwnedCardModifier(CardModifierIds.WEAKENED, 1));
        }
        if (Boolean.TRUE.equals(dto.lockedInDeck())
                && out.stream().noneMatch(m -> CardModifierIds.LOCKED_IN_DECK.equals(m.modifierId()))) {
            out.add(new OwnedCardModifier(CardModifierIds.LOCKED_IN_DECK, 1));
        }

        return List.copyOf(out);
    }

    /**
     * Join/DeckEdit 경로에서 deck 입력을 runtime canonical 형식(ownedCardId 배열)으로 정규화한다.
     * 이미 ownedCardId 목록이면 trim 후 반환하고, legacy cardId 목록이면 ownedCardId로 해석한다.
     */
    public static List<String> normalizeStoredOrRequestedDeckToOwnedCardIds(List<String> storedDeckEntries, List<OwnedCard> ownedCards) {
        if (storedDeckEntries == null) {
            return null;
        }
        DeckListFormat format = detectDeckListFormat(storedDeckEntries, ownedCards);
        if (format == DeckListFormat.CANONICAL_OWNED_CARD_IDS) {
            List<String> normalized = new ArrayList<>();
            for (String ownedCardId : storedDeckEntries) {
                if (ownedCardId == null || ownedCardId.isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds must not contain blank values");
                }
                normalized.add(ownedCardId.trim());
            }
            return List.copyOf(normalized);
        }
        return resolveCardIdsToOwnedCardIds(storedDeckEntries, ownedCards, "deckCardIds must not contain blank values");
    }

    static DeckListFormat detectDeckListFormat(List<String> deckEntries, List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> ownedById = ownedCardMap(ownedCards);
        for (String entry : deckEntries) {
            if (entry == null || entry.isBlank() || !ownedById.containsKey(entry.trim())) {
                return DeckListFormat.LEGACY_CARD_IDS;
            }
        }
        return DeckListFormat.CANONICAL_OWNED_CARD_IDS;
    }

    public static List<String> resolveCardIdsToOwnedCardIds(List<String> cardIdsRaw,
                                                             List<OwnedCard> ownedCards,
                                                             String blankValueMessage) {
        List<String> normalizedCardIds = new ArrayList<>();
        for (String cardId : cardIdsRaw) {
            if (cardId == null || cardId.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, blankValueMessage);
            }
            normalizedCardIds.add(cardId.trim());
        }

        boolean[] consumed = new boolean[ownedCards.size()];
        List<String> resolvedOwnedCardIds = new ArrayList<>(normalizedCardIds.size());
        for (String cardId : normalizedCardIds) {
            int matchedIndex = -1;
            for (int i = 0; i < ownedCards.size(); i++) {
                if (consumed[i]) {
                    continue;
                }
                if (cardId.equals(ownedCards.get(i).cardId())) {
                    matchedIndex = i;
                    break;
                }
            }
            if (matchedIndex < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "owned card unavailable: " + cardId);
            }
            consumed[matchedIndex] = true;
            resolvedOwnedCardIds.add(ownedCards.get(matchedIndex).ownedCardId());
        }
        return List.copyOf(resolvedOwnedCardIds);
    }

    private static Map<String, OwnedCard> ownedCardMap(List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            out.put(ownedCard.ownedCardId(), ownedCard);
        }
        return out;
    }

    enum DeckListFormat {
        CANONICAL_OWNED_CARD_IDS,
        LEGACY_CARD_IDS
    }

    /**
     * CharacterProfile 저장 시 currentSkillDeck 입력을 정리한다.
     *
     * <p>주의: 여기서는 포맷(ownedCardId vs legacy cardId)을 강제하지 않고 공백/빈 값만 제거한다.
     * canonical ownedCardId 해석은 세션 join/deck edit 시
     * {@link #normalizeStoredOrRequestedDeckToOwnedCardIds(List, List)} 경로에서 단일하게 수행한다.
     */
    public static List<String> normalizeStoredCurrentSkillDeck(List<String> deckEntries) {
        if (deckEntries == null) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : deckEntries) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }
}
