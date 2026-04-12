package com.example.dueltower.session.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.common.api.ApiErrorException;
import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.config.GameRules;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.service.DeckService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;
import com.example.dueltower.session.config.StarterLoadoutConfig;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Shared loadout helper used by lobby/loadout flows.
 *
 * <p>Contains parsing, validation, deck runtime mutation, and persistence helpers
 * that would otherwise be duplicated between {@link SessionLobbyService} and
 * {@link SessionLoadoutService}.</p>
 */
@Component
public class SessionLoadoutSupport {

    private static final Pattern PASSIVE_ID_FORMAT = Pattern.compile("^P\\d{3}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CharacterProfileRepository characterProfileRepository;
    private final CardService cardService;
    private final DeckService deckService;
    private final PassiveService passiveService;
    private final GameRules gameRules;
    private final StarterLoadoutConfig starterLoadoutConfig;

    public SessionLoadoutSupport(CharacterProfileRepository characterProfileRepository,
                                 CardService cardService,
                                 DeckService deckService,
                                 PassiveService passiveService,
                                 GameRules gameRules,
                                 StarterLoadoutConfig starterLoadoutConfig) {
        this.characterProfileRepository = characterProfileRepository;
        this.cardService = cardService;
        this.deckService = deckService;
        this.passiveService = passiveService;
        this.gameRules = gameRules;
        this.starterLoadoutConfig = starterLoadoutConfig;
    }

    public CharacterJoinTemplate loadCharacterJoinTemplate(Long characterIdRaw) {
        return toCharacterJoinTemplate(loadCharacterProfile(characterIdRaw));
    }

    public List<String> parsePassiveIds(List<String> passiveIdsRaw) {
        if (passiveIdsRaw == null) return List.of();
        if (passiveIdsRaw.size() > gameRules.maxPassives()) {
            throw new ResponseStatusException(BAD_REQUEST, "passiveIds allows 0 to " + gameRules.maxPassives() + " items.");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : passiveIdsRaw) {
            if (raw == null || raw.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Each passiveId must be a non-empty string.");
            }
            String id = raw.trim();
            if (!passiveService.defsMap().containsKey(id) && !PASSIVE_ID_FORMAT.matcher(id).matches()) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid passiveId format: " + id + " (expected P###, e.g. P001).");
            }
            if (!passiveService.defsMap().containsKey(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown passiveId: " + id + ". Select a passive from the available list.");
            }
            if (!normalized.add(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "Duplicate passiveId is not allowed: " + id);
            }
        }

        return List.copyOf(normalized);
    }

    public List<OwnedCard> parseOwnedCards(List<OwnedCardDto> ownedCardsRaw) {
        if (ownedCardsRaw == null || ownedCardsRaw.isEmpty()) {
            return starterLoadoutConfig.defaultOwnedCards();
        }
        return SessionNormalizationSupport.normalizeOwnedCards(ownedCardsRaw);
    }

    public List<OwnedCardModifier> toOwnedCardModifiers(OwnedCardDto dto) {
        return SessionNormalizationSupport.normalizeOwnedCardModifiers(dto);
    }

    public List<String> resolveJoinDeckOwnedCardIds(CharacterJoinTemplate characterTemplate,
                                                    List<String> requestedPresetDeckOwnedCardIdsRaw,
                                                    List<OwnedCard> ownedCards) {
        if (characterTemplate != null) {
            List<String> currentSkillDeck = characterTemplate.currentSkillDeck();
            if (currentSkillDeck == null || currentSkillDeck.isEmpty()) {
                return List.of();
            }
            return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(currentSkillDeck, ownedCards);
        }

        if (requestedPresetDeckOwnedCardIdsRaw != null) {
            return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(
                    requestedPresetDeckOwnedCardIdsRaw,
                    ownedCards
            );
        }

        return resolveCardIdsToOwnedCardIds(
                starterLoadoutConfig.defaultPresetDeckCardIds(),
                ownedCards,
                "starter defaultPresetDeckCardIds must not contain blank values"
        );
    }

    public List<String> resolveRequestedDeckOwnedCardIds(List<String> deckOwnedCardIdsRaw, List<OwnedCard> ownedCards) {
        if (deckOwnedCardIdsRaw != null) {
            return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(deckOwnedCardIdsRaw, ownedCards);
        }
        throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds is required");
    }

    public List<String> resolveStoredDeckToOwnedCardIds(List<String> storedDeckEntries, List<OwnedCard> ownedCards) {
        return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(storedDeckEntries, ownedCards);
    }

    public void validateDeckBuild(List<String> deckOwnedCardIds, List<OwnedCard> ownedCards, List<String> currentDeckOwnedCardIds) {
        if (deckOwnedCardIds.size() != gameRules.deckSize()) {
            throw new ApiErrorException(
                    BAD_REQUEST,
                    "DECK_EDIT_INVALID",
                    "VALIDATION",
                    "?깆? ?뺥솗??" + gameRules.deckSize() + "?μ쑝濡?留욎떠???⑸땲??",
                    "deck must contain exactly " + gameRules.deckSize() + " cards",
                    ApiErrorResolver.details("requiredDeckSize", gameRules.deckSize(), "actualDeckSize", deckOwnedCardIds.size())
            );
        }

        Map<String, OwnedCard> ownedById = ownedCardMap(ownedCards);
        Set<String> seenOwnedCardIds = new LinkedHashSet<>();
        List<String> deckCardIds = new ArrayList<>(deckOwnedCardIds.size());
        for (String ownedCardId : deckOwnedCardIds) {
            if (ownedCardId == null || ownedCardId.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds must not contain blank values");
            }
            String normalizedOwnedCardId = ownedCardId.trim();
            if (!seenOwnedCardIds.add(normalizedOwnedCardId)) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "DECK_EDIT_INVALID",
                        "VALIDATION",
                        "?깆뿉 媛숈? 蹂댁쑀 ?щ낯??以묐났?댁꽌 ?ｌ쓣 ???놁뒿?덈떎.",
                        "deckOwnedCardIds must not contain duplicate values: " + normalizedOwnedCardId,
                        ApiErrorResolver.details("ownedCardId", normalizedOwnedCardId)
                );
            }
            OwnedCard owned = ownedById.get(normalizedOwnedCardId);
            if (owned == null) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "OWNED_CARD_UNAVAILABLE",
                        "VALIDATION",
                        "?좏깮??移대뱶 ?щ낯???꾩옱 蹂댁쑀 紐⑸줉?먯꽌 李얠쓣 ???놁뒿?덈떎.",
                        "owned card unavailable: " + normalizedOwnedCardId,
                        ApiErrorResolver.details("ownedCardId", normalizedOwnedCardId)
                );
            }
            deckCardIds.add(owned.cardId());
        }

        Map<String, Integer> deckCounts = cardCounts(deckCardIds);
        for (var e : deckCounts.entrySet()) {
            if (e.getValue() > gameRules.maxDeckCopies()) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "DECK_EDIT_INVALID",
                        "VALIDATION",
                        "媛숈? 移대뱶???깆뿉 理쒕? " + gameRules.maxDeckCopies() + "?κ퉴吏 ?ｌ쓣 ???덉뒿?덈떎.",
                        "card copy limit exceeded: " + e.getKey() + " (max " + gameRules.maxDeckCopies() + ")",
                        ApiErrorResolver.details("cardId", e.getKey(), "maxCopies", gameRules.maxDeckCopies(), "actualCopies", e.getValue())
                );
            }
        }

        if (currentDeckOwnedCardIds != null) {
            int changedCards = calculateDeckChangedCards(currentDeckOwnedCardIds, deckOwnedCardIds);
            if (changedCards > gameRules.maxDeckEditChanges()) {
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "DECK_EDIT_INVALID",
                        "RULE",
                        "?꾩옱 ?깆뿉?쒕뒗 理쒕? " + gameRules.maxDeckEditChanges() + "?κ퉴吏留?援먯껜?????덉뒿?덈떎.",
                        "deck edit invalid: at most " + gameRules.maxDeckEditChanges() + " cards can be changed (requested " + changedCards + ")",
                        ApiErrorResolver.details("maxChangedCards", gameRules.maxDeckEditChanges(), "actualChangedCards", changedCards)
                );
            }

            Set<String> requiredLockedOwnedCardIds = lockedOwnedCardIdsRequiredInDeck(currentDeckOwnedCardIds, ownedCards);
            for (String requiredLockedOwnedCardId : requiredLockedOwnedCardIds) {
                if (!seenOwnedCardIds.contains(requiredLockedOwnedCardId)) {
                    throw new ApiErrorException(
                            BAD_REQUEST,
                            "CARD_LOCKED_IN_DECK",
                            "RULE",
                            "잠금된 카드는 현재 덱에 유지해야 합니다.",
                            "deck edit invalid: locked-in-deck card must remain in deck: " + requiredLockedOwnedCardId,
                            ApiErrorResolver.details("ownedCardId", requiredLockedOwnedCardId)
                    );
                }
            }
        }
    }

    public void validateDeckEditableState(NodeState nodeState, PlayerState ps) {
        if (nodeState == NodeState.COMBAT) {
            throw new ApiErrorException(FORBIDDEN, "DECK_EDIT_FORBIDDEN", "RULE", "?꾪닾 以묒뿉???깆쓣 ?섏젙?????놁뒿?덈떎.", "deck edit unavailable during combat", null);
        }
        if (nodeState.curseBlocked()) {
            throw new ApiErrorException(FORBIDDEN, "DECK_EDIT_FORBIDDEN", "RULE", "?꾩옱 ?二??곹깭?먯꽌???깆쓣 ?섏젙?????놁뒿?덈떎.", "deck edit unavailable during curse", null);
        }
        if (!nodeState.deckEditable()) {
            throw new ApiErrorException(FORBIDDEN, "DECK_EDIT_FORBIDDEN", "RULE", "?꾩옱 ?몃뱶 ?곹깭?먯꽌???깆쓣 ?섏젙?????놁뒿?덈떎.", "deck cannot be edited in current node state: " + nodeState.name(), ApiErrorResolver.details("nodeState", nodeState.name()));
        }
        if (ps.forgettingRequired()) {
            throw new ApiErrorException(
                    FORBIDDEN,
                    "FORGET_REQUIRED",
                    "RULE",
                    "카드 잊기 상태를 먼저 해결해야 덱을 수정할 수 있습니다.",
                    "forgetting required: owned card limit exceeded (" + ps.ownedCardCount() + "/" + ps.maxOwnedCardCount() + ")",
                    ApiErrorResolver.details("ownedCardCount", ps.ownedCardCount(), "maxOwnedCardCount", ps.maxOwnedCardCount())
            );
        }
    }

    public List<String> currentDeckOwnedCardIds(PlayerState ps) {
        return ps.deckOwnedCardIds();
    }

    public Map<String, Integer> cardCountsFromOwned(List<OwnedCard> ownedCards) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            counts.merge(ownedCard.cardId(), 1, Integer::sum);
        }
        return counts;
    }

    public Map<String, Integer> cardCounts(List<String> cardIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cardId : cardIds) {
            counts.merge(cardId, 1, Integer::sum);
        }
        return counts;
    }

    public List<String> currentDeckCardIdsFromOwnedCardIds(List<String> deckOwnedCardIds, List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> ownedById = ownedCardMap(ownedCards);
        List<String> cardIds = new ArrayList<>(deckOwnedCardIds.size());
        for (String ownedCardId : deckOwnedCardIds) {
            OwnedCard ownedCard = ownedById.get(ownedCardId);
            if (ownedCard != null) {
                cardIds.add(ownedCard.cardId());
            }
        }
        return List.copyOf(cardIds);
    }

    public void persistCharacterDeck(SessionRuntime rt, PlayerId playerId, List<String> deckOwnedCardIds, List<OwnedCard> ownedCards) {
        Long characterId = rt.findCharacterIdByPlayerId(playerId.value());
        if (characterId == null) {
            return;
        }

        CharacterProfile profile = characterProfileRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "character not found: " + characterId));

        List<String> deckCardIds = currentDeckCardIdsFromOwnedCardIds(deckOwnedCardIds, ownedCards);
        profile.setOwnedCards(toCanonicalOwnedCardsJson(ownedCards));
        profile.setCurrentSkillDeck(List.copyOf(deckOwnedCardIds));
        characterProfileRepository.save(profile);

        deckService.upsertCharacterCurrentSkillDeck(characterId, deckCardIds);
    }

    public void loadDeck(GameState state, PlayerState ps, List<String> deckOwnedCardIds) {
        Set<CardInstId> toDelete = new HashSet<>();
        toDelete.addAll(ps.deck());
        toDelete.addAll(ps.hand());
        toDelete.addAll(ps.grave());
        toDelete.addAll(ps.field());
        toDelete.addAll(ps.excluded());

        ps.deck().clear();
        ps.hand().clear();
        ps.grave().clear();
        ps.field().clear();
        ps.excluded().clear();

        for (CardInstId id : toDelete) {
            state.cardInstances().remove(id);
        }

        Map<String, OwnedCard> ownedById = ownedCardMap(ps.ownedCards());
        for (String ownedCardId : deckOwnedCardIds) {
            OwnedCard ownedCard = ownedById.get(ownedCardId);
            if (ownedCard == null) {
                throw new IllegalStateException("owned card slot unavailable while loading deck: " + ownedCardId);
            }
            addCardToDeck(state, ps, new CardDefId(ownedCard.cardId()), ownedCard.ownedCardId(), ownedCard.modifiers());
        }
    }

    public void addCardToEx(GameState state, PlayerState ps, CardDefId defId) {
        CardInstId previousEx = ps.exCard();
        if (previousEx != null) {
            state.cardInstances().remove(previousEx);
            ps.exCard(null);
        }

        ZoneOps.createCardInZone(state, ps, defId, Zone.EX);
    }

    public void shuffleDeck(GameState state, PlayerState ps) {
        shuffleDeck(state, ps, state.seed());
    }

    public void shuffleDeck(GameState state, PlayerState ps, long seed) {
        List<CardInstId> list = new ArrayList<>(ps.deck());
        ps.deck().clear();
        Collections.shuffle(list, new Random(seed ^ ps.playerId().value().hashCode()));
        for (CardInstId id : list) ps.deck().addLast(id);
    }

    public String normalizeExCardId(String raw) {
        return (raw == null || raw.isBlank()) ? starterLoadoutConfig.defaultExCardId() : raw.trim();
    }

    public String resolveCurrentExCardId(GameState state, PlayerState ps) {
        if (ps.exCard() == null) {
            return starterLoadoutConfig.defaultExCardId();
        }
        CardInstance exInst = state.card(ps.exCard());
        if (exInst == null || exInst.defId() == null || exInst.defId().value() == null || exInst.defId().value().isBlank()) {
            return starterLoadoutConfig.defaultExCardId();
        }
        return exInst.defId().value().trim();
    }

    public List<String> resolveCardIdsToOwnedCardIds(List<String> cardIdsRaw,
                                                     List<OwnedCard> ownedCards,
                                                     String blankValueMessage) {
        return SessionNormalizationSupport.resolveCardIdsToOwnedCardIds(cardIdsRaw, ownedCards, blankValueMessage);
    }

    public void validateExCardId(String exCardIdRaw) {
        String exCardId = normalizeExCardId(exCardIdRaw);
        if (!cardService.asMap().containsKey(new CardDefId(exCardId))) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid exCardId: " + exCardId);
        }
    }

    private int calculateDeckChangedCards(List<String> currentDeckOwnedCardIds, List<String> newDeckOwnedCardIds) {
        Set<String> next = new LinkedHashSet<>(newDeckOwnedCardIds);
        int changed = 0;
        for (String ownedCardId : currentDeckOwnedCardIds) {
            if (!next.contains(ownedCardId)) {
                changed++;
            }
        }
        return changed;
    }

    private Set<String> lockedOwnedCardIdsRequiredInDeck(List<String> currentDeckOwnedCardIds, List<OwnedCard> ownedCards) {
        Set<String> currentDeckSet = new LinkedHashSet<>(currentDeckOwnedCardIds);
        Set<String> out = new LinkedHashSet<>();
        for (OwnedCard owned : ownedCards) {
            if (owned.lockedInDeck() && currentDeckSet.contains(owned.ownedCardId())) {
                out.add(owned.ownedCardId());
            }
        }
        return out;
    }

    private CharacterProfile loadCharacterProfile(Long characterIdRaw) {
        if (characterIdRaw == null || characterIdRaw <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "characterId must be a positive number");
        }
        return characterProfileRepository.findById(characterIdRaw)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "character not found: " + characterIdRaw));
    }

    private CharacterJoinTemplate toCharacterJoinTemplate(CharacterProfile profile) {
        List<String> passiveIds = new ArrayList<>(2);
        if (profile.getTrait1() != null && !profile.getTrait1().isBlank()) passiveIds.add(profile.getTrait1().trim());
        if (profile.getTrait2() != null && !profile.getTrait2().isBlank()) passiveIds.add(profile.getTrait2().trim());

        List<OwnedCardDto> ownedCards = parseOwnedCardsJson(profile.getOwnedCards());
        List<String> currentSkillDeck = SessionNormalizationSupport.normalizeStoredCurrentSkillDeck(profile.getCurrentSkillDeck());
        String exCardId = parseExCardId(profile.getExCard());

        return new CharacterJoinTemplate(
                List.copyOf(passiveIds),
                currentSkillDeck == null ? null : List.copyOf(currentSkillDeck),
                exCardId,
                ownedCards
        );
    }

    public List<OwnedCardDto> parseOwnedCardsJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<JsonNode> nodes = JSON.readValue(raw, new TypeReference<>() {});
            List<OwnedCardDto> out = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                JsonNode node = nodes.get(i);
                if (node == null || node.isNull()) continue;
                if (node.isTextual()) {
                    String cardId = node.asText("").trim();
                    if (!cardId.isEmpty()) out.add(new OwnedCardDto(null, cardId, List.of(), false, false, false, true, null));
                    continue;
                }
                String cardId = node.path("cardId").asText("").trim();
                if (cardId.isEmpty()) {
                    throw invalidPersistedOwnedCards("entry[" + i + "] has missing cardId");
                }
                String ownedCardId = node.path("ownedCardId").asText("").trim();
                List<OwnedCardModifierDto> modifiers = parseOwnedCardModifierDtos(node.path("modifiers"));
                boolean strengthened = node.path("strengthened").asBoolean(false);
                boolean weakened = node.path("weakened").asBoolean(false);
                boolean lockedInDeck = node.path("lockedInDeck").asBoolean(false);
                out.add(new OwnedCardDto(
                        ownedCardId.isEmpty() ? null : ownedCardId,
                        cardId,
                        modifiers,
                        strengthened,
                        weakened,
                        lockedInDeck,
                        true,
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

    private ResponseStatusException invalidPersistedOwnedCards(String detail) {
        return new ResponseStatusException(BAD_REQUEST, "invalid persisted ownedCards payload: " + detail);
    }

    private String parseExCardId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode node = JSON.readTree(raw);
            if (node == null || node.isNull()) return null;
            if (node.isTextual()) {
                String exId = node.asText("").trim();
                return exId.isEmpty() ? null : exId;
            }
            String exId = node.path("id").asText("").trim();
            return exId.isEmpty() ? null : exId;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "character exCard JSON is invalid");
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
            if (modifierId.isEmpty()) {
                continue;
            }
            int value = modifierNode.path("value").asInt(0);
            out.add(new OwnedCardModifierDto(modifierId, value));
        }
        return List.copyOf(out);
    }

    private String toCanonicalOwnedCardsJson(List<OwnedCard> ownedCards) {
        ArrayNode out = JSON.createArrayNode();
        for (OwnedCard ownedCard : ownedCards) {
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

    private Map<String, OwnedCard> ownedCardMap(List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            out.put(ownedCard.ownedCardId(), ownedCard);
        }
        return out;
    }

    private void addCardToDeck(GameState state,
                               PlayerState ps,
                               CardDefId defId,
                               String sourceOwnedCardId,
                               List<OwnedCardModifier> modifiers) {
        ZoneOps.createCardInZone(state, ps, defId, Zone.DECK, sourceOwnedCardId, modifiers);
    }

    public record CharacterJoinTemplate(
            List<String> passiveIds,
            List<String> currentSkillDeck,
            String exCardId,
            List<OwnedCardDto> ownedCards
    ) {}
}
