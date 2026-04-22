package com.example.dueltower.character.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import com.example.dueltower.session.service.SessionNormalizationSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CharacterCurrentSkillDeckReadService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern GENERATED_OWNED_CARD_ID_PATTERN =
            Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /**
     * 저장된 currentSkillDeck을 cardId 목록으로 해석한다.
     * ownedCardId 기반 저장값이면 ownedCards를 통해 cardId로 변환하고, legacy/apply cardId 기반 저장값이면 정규화한 cardId를 반환한다.
     */
    public List<String> resolveStoredCurrentSkillDeckToCardIds(List<String> storedCurrentSkillDeck, String ownedCardsJson) {
        return resolveStoredCurrentSkillDeckToCardIds(storedCurrentSkillDeck, parseOwnedCardsJson(ownedCardsJson));
    }

    /**
     * 저장된 currentSkillDeck을 cardId 목록으로 해석한다.
     * ownedCardId 기반 저장값이면 ownedCards를 통해 cardId로 변환하고, legacy/apply cardId 기반 저장값이면 정규화한 cardId를 반환한다.
     */
    public List<String> resolveStoredCurrentSkillDeckToCardIds(List<String> storedCurrentSkillDeck, List<OwnedCard> ownedCards) {
        List<String> normalized = normalizeStoredEntries(storedCurrentSkillDeck);
        if (normalized.isEmpty()) {
            return List.of();
        }

        return resolveStoredCurrentSkillDeckPreview(normalized, ownedCards).cardIds();
    }

    /**
     * 저장된 currentSkillDeck을 session/loadout용 ownedCardId 목록으로 해석한다.
     * cardId 기반 저장값이면 ownedCards를 사용해 ownedCardId로 변환한다.
     */
    public List<String> resolveStoredCurrentSkillDeckToOwnedCardIds(List<String> storedCurrentSkillDeck, String ownedCardsJson) {
        return resolveStoredCurrentSkillDeckToOwnedCardIds(storedCurrentSkillDeck, parseOwnedCardsJson(ownedCardsJson));
    }

    /**
     * 저장된 currentSkillDeck을 session/loadout용 ownedCardId 목록으로 해석한다.
     * cardId 기반 저장값이면 ownedCards를 사용해 ownedCardId로 변환한다.
     */
    public List<String> resolveStoredCurrentSkillDeckToOwnedCardIds(List<String> storedCurrentSkillDeck, List<OwnedCard> ownedCards) {
        List<String> normalized = normalizeStoredEntries(storedCurrentSkillDeck);
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (ownedCards == null || ownedCards.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCards is required to resolve currentSkillDeck to ownedCardIds");
        }
        return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(normalized, ownedCards);
    }

    /**
     * 화면 표시/preview용 currentSkillDeck 정보를 cardId 기준으로 만든다.
     */
    public CurrentSkillDeckPreview previewStoredCurrentSkillDeck(List<String> storedCurrentSkillDeck, String ownedCardsJson) {
        CurrentSkillDeckPreview resolved = resolveStoredCurrentSkillDeckPreview(
                normalizeStoredEntries(storedCurrentSkillDeck),
                parseOwnedCardsJson(ownedCardsJson)
        );
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cardId : resolved.cardIds()) {
            counts.merge(cardId, 1, Integer::sum);
        }
        return new CurrentSkillDeckPreview(
                resolved.cardIds(),
                counts,
                resolved.cardIds().size(),
                resolved.unresolvedEntryCount(),
                resolved.unresolvedEntryCount() > 0
        );
    }

    private static CurrentSkillDeckPreview resolveStoredCurrentSkillDeckPreview(
            List<String> normalized,
            List<OwnedCard> ownedCards
    ) {
        if (normalized.isEmpty()) {
            return CurrentSkillDeckPreview.empty();
        }

        Map<String, OwnedCard> ownedById = ownedCardMap(ownedCards);
        List<String> cardIds = new ArrayList<>(normalized.size());
        int unresolvedEntryCount = 0;

        for (String entry : normalized) {
            OwnedCard ownedCard = ownedById.get(entry);
            if (ownedCard != null) {
                cardIds.add(ownedCard.cardId());
                continue;
            }

            if (looksLikeOwnedCardId(entry)) {
                unresolvedEntryCount++;
                continue;
            }

            cardIds.add(entry);
        }

        /*
         * Mixed ownedCardId state can happen after ownedCards changes or stale client data.
         * In that case unresolved ownedCardId-looking values are intentionally dropped instead of being
         * treated as cardIds, because leaking oc-* / UUID values into preview or scoring corrupts read-side results.
         */
        return CurrentSkillDeckPreview.of(cardIds, unresolvedEntryCount);
    }

    private List<OwnedCard> parseOwnedCardsJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<JsonNode> nodes = JSON.readValue(raw, new TypeReference<>() {});
            List<OwnedCardDto> out = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                JsonNode node = nodes.get(i);
                if (node == null || node.isNull()) {
                    continue;
                }
                if (node.isTextual()) {
                    String cardId = node.asText("").trim();
                    if (!cardId.isEmpty()) {
                        out.add(new OwnedCardDto(null, cardId, List.of(), false, false, false, true, null));
                    }
                    continue;
                }
                String cardId = node.path("cardId").asText("").trim();
                if (cardId.isEmpty()) {
                    throw invalidPersistedOwnedCards("entry[" + i + "] has missing cardId");
                }
                String ownedCardId = node.path("ownedCardId").asText("").trim();
                out.add(new OwnedCardDto(
                        ownedCardId.isEmpty() ? null : ownedCardId,
                        cardId,
                        parseOwnedCardModifierDtos(node.path("modifiers")),
                        node.path("strengthened").asBoolean(false),
                        node.path("weakened").asBoolean(false),
                        node.path("lockedInDeck").asBoolean(false),
                        true,
                        null
                ));
            }
            return SessionNormalizationSupport.normalizeOwnedCards(out);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw invalidPersistedOwnedCards("malformed JSON");
        }
    }

    private List<OwnedCardModifierDto> parseOwnedCardModifierDtos(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<OwnedCardModifierDto> out = new ArrayList<>();
        for (JsonNode modifierNode : node) {
            if (modifierNode == null || modifierNode.isNull()) {
                continue;
            }
            String modifierId = modifierNode.path("modifierId").asText("").trim();
            if (!modifierId.isEmpty()) {
                out.add(new OwnedCardModifierDto(modifierId, modifierNode.path("value").asInt(0)));
            }
        }
        return List.copyOf(out);
    }

    private static List<String> normalizeStoredEntries(List<String> storedCurrentSkillDeck) {
        if (storedCurrentSkillDeck == null || storedCurrentSkillDeck.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(storedCurrentSkillDeck.size());
        for (String value : storedCurrentSkillDeck) {
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

    private static boolean looksLikeOwnedCardId(String value) {
        return value.startsWith("oc-") || GENERATED_OWNED_CARD_ID_PATTERN.matcher(value).matches();
    }

    private static Map<String, OwnedCard> ownedCardMap(List<OwnedCard> ownedCards) {
        if (ownedCards == null || ownedCards.isEmpty()) {
            return Map.of();
        }
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            if (ownedCard != null && ownedCard.ownedCardId() != null && !ownedCard.ownedCardId().isBlank()) {
                out.put(ownedCard.ownedCardId().trim(), ownedCard);
            }
        }
        return out;
    }

    private static ResponseStatusException invalidPersistedOwnedCards(String detail) {
        return new ResponseStatusException(BAD_REQUEST, "invalid persisted ownedCards payload: " + detail);
    }

    public record CurrentSkillDeckPreview(
            List<String> cardIds,
            Map<String, Integer> cardCounts,
            int totalCards,
            int unresolvedEntryCount,
            boolean hasUnresolvedEntries
    ) {
        private static CurrentSkillDeckPreview empty() {
            return new CurrentSkillDeckPreview(List.of(), Map.of(), 0, 0, false);
        }

        private static CurrentSkillDeckPreview of(List<String> cardIds, int unresolvedEntryCount) {
            return new CurrentSkillDeckPreview(
                    List.copyOf(cardIds),
                    Map.of(),
                    cardIds.size(),
                    unresolvedEntryCount,
                    unresolvedEntryCount > 0
            );
        }
    }
}
