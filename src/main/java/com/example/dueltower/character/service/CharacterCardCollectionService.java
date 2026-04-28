package com.example.dueltower.character.service;

import com.example.dueltower.character.domain.CharacterOwnedCard;
import com.example.dueltower.character.domain.CharacterOwnedCardModifier;
import com.example.dueltower.character.dto.CharacterOwnedCardModifierResponse;
import com.example.dueltower.character.dto.CharacterOwnedCardResponse;
import com.example.dueltower.character.repository.CharacterOwnedCardModifierRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import com.example.dueltower.session.service.SessionNormalizationSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CharacterCardCollectionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CharacterOwnedCardRepository ownedCardRepository;
    private final CharacterOwnedCardModifierRepository ownedCardModifierRepository;

    public CharacterCardCollectionService(CharacterOwnedCardRepository ownedCardRepository,
                                          CharacterOwnedCardModifierRepository ownedCardModifierRepository) {
        this.ownedCardRepository = ownedCardRepository;
        this.ownedCardModifierRepository = ownedCardModifierRepository;
    }

    @Transactional
    public void replaceOwnedCards(Long characterId, List<OwnedCardDto> ownedCardDtos) {
        requireCharacterId(characterId);
        List<OwnedCardDto> source = ownedCardDtos == null ? List.of() : ownedCardDtos;
        List<OwnedCard> ownedCards = SessionNormalizationSupport.normalizeOwnedCards(source);
        rejectDuplicateOwnedCardIds(ownedCards);

        deleteOwnedCards(characterId);

        List<CharacterOwnedCard> cardRows = new ArrayList<>(ownedCards.size());
        List<CharacterOwnedCardModifier> modifierRows = new ArrayList<>();
        for (int i = 0; i < ownedCards.size(); i++) {
            OwnedCard ownedCard = ownedCards.get(i);
            OwnedCardDto dto = source.get(i);
            cardRows.add(toEntity(characterId, ownedCard, dto.forgettable()));
            for (OwnedCardModifier modifier : ownedCard.modifiers()) {
                modifierRows.add(CharacterOwnedCardModifier.builder()
                        .ownedCardId(ownedCard.ownedCardId())
                        .modifierId(modifier.modifierId())
                        .value(modifier.value())
                        .build());
            }
        }

        ownedCardRepository.saveAll(cardRows);
        ownedCardModifierRepository.saveAll(modifierRows);
    }

    @Transactional
    public void replaceOwnedCardsFromJson(Long characterId, String ownedCardsJson) {
        replaceOwnedCards(characterId, parseOwnedCardsJson(ownedCardsJson));
    }

    @Transactional(readOnly = true)
    public List<OwnedCard> toRuntimeOwnedCards(Long characterId) {
        return SessionNormalizationSupport.normalizeOwnedCards(toOwnedCardDtos(characterId));
    }

    @Transactional(readOnly = true)
    public List<OwnedCardDto> toOwnedCardDtos(Long characterId) {
        requireCharacterId(characterId);
        List<CharacterOwnedCard> cardRows = ownedCardRepository.findByCharacterIdOrderByCreateDateAscOwnedCardIdAsc(characterId);
        if (cardRows.isEmpty()) {
            return List.of();
        }

        Map<String, List<OwnedCardModifierDto>> modifiersByOwnedCardId = modifierDtosByOwnedCardId(cardRows);
        List<OwnedCardDto> out = new ArrayList<>(cardRows.size());
        for (CharacterOwnedCard row : cardRows) {
            out.add(new OwnedCardDto(
                    row.getOwnedCardId(),
                    row.getCardId(),
                    modifiersByOwnedCardId.getOrDefault(row.getOwnedCardId(), List.of()),
                    row.isStrengthened(),
                    row.isWeakened(),
                    row.isLockedInDeck(),
                    row.isForgettable(),
                    null
            ));
        }
        return List.copyOf(out);
    }

    @Transactional(readOnly = true)
    public List<CharacterOwnedCardResponse> toOwnedCardResponses(Long characterId) {
        return toOwnedCardDtos(characterId).stream()
                .map(CharacterCardCollectionService::toOwnedCardResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String toOwnedCardsJson(Long characterId) {
        ArrayNode out = JSON.createArrayNode();
        for (OwnedCard ownedCard : toRuntimeOwnedCards(characterId)) {
            ObjectNode node = out.addObject();
            node.put("ownedCardId", ownedCard.ownedCardId());
            node.put("cardId", ownedCard.cardId());
            node.put("strengthened", ownedCard.strengthened());
            node.put("weakened", ownedCard.weakened());
            node.put("lockedInDeck", ownedCard.lockedInDeck());

            ArrayNode modifiers = node.putArray("modifiers");
            for (OwnedCardModifier modifier : ownedCard.modifiers()) {
                ObjectNode modifierNode = modifiers.addObject();
                modifierNode.put("modifierId", modifier.modifierId());
                modifierNode.put("value", modifier.value());
            }
        }
        return out.toString();
    }

    @Transactional(readOnly = true)
    public Map<String, OwnedCard> ownedCardMap(Long characterId) {
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : toRuntimeOwnedCards(characterId)) {
            out.put(ownedCard.ownedCardId(), ownedCard);
        }
        return Map.copyOf(out);
    }

    @Transactional(readOnly = true)
    public boolean hasOwnedCard(Long characterId, String ownedCardId) {
        requireCharacterId(characterId);
        if (ownedCardId == null || ownedCardId.isBlank()) {
            return false;
        }
        return ownedCardRepository.existsByCharacterIdAndOwnedCardId(characterId, ownedCardId.trim());
    }

    @Transactional
    public void deleteOwnedCards(Long characterId) {
        requireCharacterId(characterId);
        List<String> ownedCardIds = ownedCardRepository.findByCharacterId(characterId).stream()
                .map(CharacterOwnedCard::getOwnedCardId)
                .toList();
        if (!ownedCardIds.isEmpty()) {
            ownedCardModifierRepository.deleteByOwnedCardIdIn(ownedCardIds);
        }
        ownedCardRepository.deleteByCharacterId(characterId);
    }

    private static CharacterOwnedCard toEntity(Long characterId, OwnedCard ownedCard, Boolean forgettable) {
        return CharacterOwnedCard.builder()
                .ownedCardId(ownedCard.ownedCardId())
                .characterId(characterId)
                .cardId(ownedCard.cardId())
                .strengthened(ownedCard.strengthened())
                .weakened(ownedCard.weakened())
                .lockedInDeck(ownedCard.lockedInDeck())
                .forgettable(forgettable == null || forgettable)
                .build();
    }

    private static CharacterOwnedCardResponse toOwnedCardResponse(OwnedCardDto dto) {
        List<CharacterOwnedCardModifierResponse> modifiers = dto.modifiers() == null ? List.of() : dto.modifiers().stream()
                .map(modifier -> new CharacterOwnedCardModifierResponse(
                        modifier.modifierId(),
                        modifier.value() == null ? 0 : modifier.value()
                ))
                .toList();
        return new CharacterOwnedCardResponse(
                dto.ownedCardId(),
                dto.cardId(),
                modifiers,
                Boolean.TRUE.equals(dto.strengthened()),
                Boolean.TRUE.equals(dto.weakened()),
                Boolean.TRUE.equals(dto.lockedInDeck()),
                dto.forgettable() == null || dto.forgettable(),
                dto.notForgettableReason()
        );
    }

    private Map<String, List<OwnedCardModifierDto>> modifierDtosByOwnedCardId(List<CharacterOwnedCard> cardRows) {
        List<String> ownedCardIds = cardRows.stream()
                .map(CharacterOwnedCard::getOwnedCardId)
                .toList();
        List<CharacterOwnedCardModifier> modifiers = ownedCardModifierRepository.findByOwnedCardIdInOrderByIdAsc(ownedCardIds);

        Map<String, List<OwnedCardModifierDto>> mutable = new LinkedHashMap<>();
        for (CharacterOwnedCardModifier modifier : modifiers) {
            mutable.computeIfAbsent(modifier.getOwnedCardId(), ignored -> new ArrayList<>())
                    .add(new OwnedCardModifierDto(modifier.getModifierId(), modifier.getValue()));
        }

        Map<String, List<OwnedCardModifierDto>> out = new LinkedHashMap<>();
        for (var entry : mutable.entrySet()) {
            out.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return out;
    }

    private static List<OwnedCardDto> parseOwnedCardsJson(String raw) {
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
                        node.path("forgettable").isMissingNode() ? true : node.path("forgettable").asBoolean(true),
                        null
                ));
            }
            return List.copyOf(out);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw invalidPersistedOwnedCards("malformed JSON");
        }
    }

    private static List<OwnedCardModifierDto> parseOwnedCardModifierDtos(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<OwnedCardModifierDto> out = new ArrayList<>();
        for (JsonNode modifierNode : node) {
            if (modifierNode == null || modifierNode.isNull()) {
                continue;
            }
            String modifierId = modifierNode.path("modifierId").asText("").trim();
            if (modifierId.isEmpty()) {
                continue;
            }
            out.add(new OwnedCardModifierDto(modifierId, modifierNode.path("value").asInt(0)));
        }
        return List.copyOf(out);
    }

    private static void rejectDuplicateOwnedCardIds(List<OwnedCard> ownedCards) {
        Set<String> seen = new LinkedHashSet<>();
        for (OwnedCard ownedCard : ownedCards) {
            if (!seen.add(ownedCard.ownedCardId())) {
                throw new ResponseStatusException(BAD_REQUEST, "ownedCards.ownedCardId must be unique: " + ownedCard.ownedCardId());
            }
        }
    }

    private static ResponseStatusException invalidPersistedOwnedCards(String detail) {
        return new ResponseStatusException(BAD_REQUEST, "invalid persisted ownedCards payload: " + detail);
    }

    private static void requireCharacterId(Long characterId) {
        if (characterId == null || characterId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "characterId must be a positive number");
        }
    }
}
