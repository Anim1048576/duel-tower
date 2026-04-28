package com.example.dueltower.support;

import com.example.dueltower.character.service.CharacterCardCollectionService;
import com.example.dueltower.character.service.CharacterLoadoutService;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.session.dto.OwnedCardDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class CharacterLoadoutTestFixtures {

    public static final String EMPTY_OWNED_CARDS_JSON = "[]";
    public static final String EMPTY_EX_CARD_JSON = "{}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private CharacterLoadoutTestFixtures() {
    }

    public static void seedLoadout(
            CharacterCardCollectionService cardCollectionService,
            CharacterLoadoutService loadoutService,
            Long characterId,
            String ownedCardsJson,
            List<String> currentSkillDeckCardIds,
            String exCardId
    ) {
        seedOwnedCards(cardCollectionService, characterId, ownedCardsJson);
        seedCurrentSkillDeckFromCardIds(cardCollectionService, loadoutService, characterId, currentSkillDeckCardIds);
        seedExCard(loadoutService, characterId, exCardId);
    }

    public static void seedOwnedCards(
            CharacterCardCollectionService cardCollectionService,
            Long characterId,
            String ownedCardsJson
    ) {
        cardCollectionService.replaceOwnedCards(characterId, ownedCardDtos(ownedCardsJson));
    }

    public static void seedCurrentSkillDeckFromCardIds(
            CharacterCardCollectionService cardCollectionService,
            CharacterLoadoutService loadoutService,
            Long characterId,
            List<String> cardIds
    ) {
        if (cardIds == null || cardIds.isEmpty()) {
            loadoutService.clearCurrentSkillDeck(characterId);
            return;
        }
        loadoutService.replaceCurrentSkillDeckFromOwnedCardIds(
                characterId,
                resolveOwnedCardIdsForCardIds(cardCollectionService, characterId, cardIds)
        );
    }

    public static void seedExCard(CharacterLoadoutService loadoutService, Long characterId, String exCardId) {
        if (exCardId == null || exCardId.isBlank()) {
            loadoutService.clearExCard(characterId);
            return;
        }
        loadoutService.replaceExCard(characterId, exCardId);
    }

    public static List<String> resolveOwnedCardIdsForCardIds(
            CharacterCardCollectionService cardCollectionService,
            Long characterId,
            List<String> cardIds
    ) {
        List<OwnedCard> ownedCards = cardCollectionService.toRuntimeOwnedCards(characterId);
        boolean[] used = new boolean[ownedCards.size()];
        List<String> resolved = new ArrayList<>(cardIds.size());
        for (String cardId : cardIds) {
            int matchedIndex = -1;
            for (int i = 0; i < ownedCards.size(); i++) {
                if (!used[i] && cardId.equals(ownedCards.get(i).cardId())) {
                    matchedIndex = i;
                    break;
                }
            }
            if (matchedIndex < 0) {
                throw new IllegalArgumentException("owned card unavailable for cardId: " + cardId);
            }
            used[matchedIndex] = true;
            resolved.add(ownedCards.get(matchedIndex).ownedCardId());
        }
        return List.copyOf(resolved);
    }

    private static List<OwnedCardDto> ownedCardDtos(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<OwnedCardDto> out = new ArrayList<>();
            for (JsonNode node : root) {
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
                    continue;
                }
                String ownedCardId = node.path("ownedCardId").asText("").trim();
                out.add(new OwnedCardDto(
                        ownedCardId.isEmpty() ? null : ownedCardId,
                        cardId,
                        List.of(),
                        false,
                        false,
                        false,
                        true,
                        null
                ));
            }
            return List.copyOf(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid owned card fixture JSON", e);
        }
    }
}
