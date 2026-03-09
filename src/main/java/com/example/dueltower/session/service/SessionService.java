package com.example.dueltower.session.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.card.model.OwnedCardModifierSemantics;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.service.DeckService;
import com.example.dueltower.content.cardmodifier.service.CardModifierService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;

@Service
@Slf4j
public class SessionService {

    private static final int DECK_SIZE = 12;
    private static final int MAX_DECK_COPIES = 3;
    private static final int MAX_DECK_EDIT_CHANGES = 2;
    private static final Pattern PASSIVE_ID_FORMAT = Pattern.compile("^P\\d{3}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CharacterProfileRepository characterProfileRepository;
    private final CardService cardService;
    private final DeckService deckService;
    private final StatusService statusService;
    private final KeywordService keywordService;
    private final PassiveService passiveService;
    private final CardModifierService cardModifierService;
    private final Duration sessionTtl;
    private final Duration cleanupInterval;

    // code -> runtime (in-memory)
    private final Map<String, SessionRuntime> sessions = new ConcurrentHashMap<>();

    private final SecureRandom rnd = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    public SessionService(CharacterProfileRepository characterProfileRepository,
                          CardService cardService,
                          DeckService deckService,
                          StatusService statusService,
                          KeywordService keywordService,
                          PassiveService passiveService,
                          CardModifierService cardModifierService,
                          @Value("${duel.session.ttl:30m}") Duration sessionTtl,
                          @Value("${duel.session.cleanup-interval:5m}") Duration cleanupInterval) {
        this.characterProfileRepository = characterProfileRepository;
        this.cardService = cardService;
        this.deckService = deckService;
        this.statusService = statusService;
        this.keywordService = keywordService;
        this.passiveService = passiveService;
        this.cardModifierService = cardModifierService;
        this.sessionTtl = sessionTtl;
        this.cleanupInterval = cleanupInterval;
    }

    public SessionRuntime createSession(String gmId) {
        evictExpiredSessions();
        for (int attempt = 0; attempt < 10_000; attempt++) {
            String code = generateCode(8);

            EngineContext ctx = new EngineContext(
                    cardService.asMap(),
                    cardService.effectsMap(),
                    statusService.defsMap(),
                    statusService.effectsMap(),
                    keywordService.defsMap(),
                    keywordService.effectsMap(),
                    passiveService.defsMap(),
                    passiveService.effectsMap(),
                    cardModifierService.defsMap(),
                    cardModifierService.effectsMap()
            );
            GameState state = new GameState(new SessionId(UUID.randomUUID()), rnd.nextLong());
            SessionRuntime rt = new SessionRuntime(code, gmId, generateGmToken(), state, ctx);

            if (sessions.putIfAbsent(code, rt) == null) {
                log.debug("created session code={} gmId={} sessionId={} seed={}",
                        code, gmId, state.sessionId().value(), state.seed());
                return rt;
            }
        }

        log.warn("failed to allocate session code gmId={} after max attempts", gmId);
        throw new ResponseStatusException(SERVICE_UNAVAILABLE, "failed to allocate session code");
    }

    public SessionRuntime get(String code) {
        evictExpiredSessions();
        SessionRuntime rt = sessions.get(code);
        if (rt == null) throw new ResponseStatusException(NOT_FOUND, "session not found");
        if (isExpired(rt)) {
            sessions.remove(code, rt);
            throw new ResponseStatusException(GONE, "session expired");
        }
        rt.touchAccess();
        return rt;
    }

    public <T> T withSessionLock(String code, Function<SessionRuntime, T> reader) {
        SessionRuntime rt = get(code);
        return rt.withLock(() -> reader.apply(rt));
    }

    public GameState join(String code,
                          String playerIdRaw,
                          Long characterIdRaw,
                          List<String> passiveIdsRaw,
                          List<String> requestedPresetDeckOwnedCardIdsRaw,
                          String presetExCardIdRaw,
                          List<OwnedCardDto> ownedCardsRaw) {
        if (playerIdRaw == null || playerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        SessionRuntime rt = get(code);
        PlayerId pid = new PlayerId(playerIdRaw.trim());

        CharacterJoinTemplate characterTemplate = (characterIdRaw == null)
                ? null
                : toCharacterJoinTemplate(loadCharacterProfile(characterIdRaw));

        List<String> passiveIds = parsePassiveIds(characterTemplate != null ? characterTemplate.passiveIds() : passiveIdsRaw);

        return rt.withLock(() -> {
            GameState state = rt.state();

            if (state.players().containsKey(pid)) {
                List<String> existingPassiveIds = state.player(pid).passiveIds();
                if (!existingPassiveIds.equals(passiveIds)) {
                    throw new ResponseStatusException(
                            BAD_REQUEST,
                            "Passives are fixed at first join and cannot be changed later. Leave passiveIds empty or resend the same values."
                    );
                }
                return state;
            }

            PlayerState ps = new PlayerState(pid);
            ps.passiveIds(passiveIds);
            if (characterIdRaw != null) {
                rt.bindCharacterId(pid.value(), characterIdRaw);
            }

            List<OwnedCard> ownedCards = parseOwnedCards(characterTemplate != null ? characterTemplate.ownedCards() : ownedCardsRaw);
            ps.ownedCards(ownedCards);

            List<String> deckOwnedCardIds = resolveJoinDeckOwnedCardIds(
                    characterTemplate,
                    requestedPresetDeckOwnedCardIdsRaw,
                    ps.ownedCards()
            );
            boolean allowEmptyCharacterDeck = characterTemplate != null && deckOwnedCardIds.isEmpty();
            if (!allowEmptyCharacterDeck) {
                validateDeckBuild(deckOwnedCardIds, ps.ownedCards(), null);
            }

            state.players().put(pid, ps);
            ps.deckOwnedCardIds(deckOwnedCardIds);
            loadDeck(state, ps, deckOwnedCardIds);
            String exCardId = (characterTemplate != null) ? characterTemplate.exCardId() : presetExCardIdRaw;
            addCardToEx(state, ps, new CardDefId(normalizeExCardId(exCardId)));

            shuffleDeck(state, ps);
            return state;
        });
    }

    public String issuePlayerToken(String code, String playerIdRaw) {
        if (playerIdRaw == null || playerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        SessionRuntime rt = get(code);
        String playerId = playerIdRaw.trim();
        return rt.withLock(() -> {
            if (!rt.state().players().containsKey(new PlayerId(playerId))) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }
            return rt.issuePlayerToken(playerId);
        });
    }

    public String resolvePlayerIdByToken(String code, String playerTokenRaw) {
        if (playerTokenRaw == null || playerTokenRaw.isBlank()) {
            return null;
        }
        SessionRuntime rt = get(code);
        String token = playerTokenRaw.trim();
        return rt.withLock(() -> rt.findPlayerIdByToken(token));
    }

    public GameState updateDeck(String code,
                                String actorPlayerIdRaw,
                                String targetPlayerIdRaw,
                                List<String> requestedDeckOwnedCardIdsRaw) {
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());

        SessionRuntime rt = get(code);
        return rt.withLock(() -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only edit their own deck");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            validateDeckEditableState(state.nodeState(), ps);

            List<String> deckOwnedCardIds = normalizeDeckOwnedCardIds(requestedDeckOwnedCardIdsRaw);
            validateDeckBuild(deckOwnedCardIds, ps.ownedCards(), currentDeckOwnedCardIds(ps));
            ps.deckOwnedCardIds(deckOwnedCardIds);
            loadDeck(state, ps, deckOwnedCardIds);
            shuffleDeck(state, ps);
            persistCharacterDeck(rt, target, deckOwnedCardIds, ps.ownedCards());
            return state;
        });
    }


    public GameState forgetOwnedCard(String code,
                                     String actorPlayerIdRaw,
                                     String targetPlayerIdRaw,
                                     Integer ownedCardIndexRaw) {
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        if (ownedCardIndexRaw == null) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCardIndex is required");
        }

        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());

        SessionRuntime rt = get(code);
        return rt.withLock(() -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only forget their own cards");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            List<OwnedCard> ownedCards = new ArrayList<>(ps.ownedCards());
            if (ownedCardIndexRaw < 0 || ownedCardIndexRaw >= ownedCards.size()) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "ownedCardIndex out of range: " + ownedCardIndexRaw + " (size " + ownedCards.size() + ")");
            }

            Map<String, Integer> ownedCounts = cardCountsFromOwned(ownedCards);
            List<String> currentDeckOwnedCardIds = currentDeckOwnedCardIds(ps);
            Map<String, Integer> deckCounts = cardCounts(currentDeckCardIdsFromOwnedCardIds(currentDeckOwnedCardIds, ownedCards));

            if (ps.forgettingRequired() && !OwnedCardForgetPolicy.hasForgettableCardWithDeckMembership(
                    ownedCards,
                    ownedCounts,
                    deckCounts,
                    new LinkedHashSet<>(currentDeckOwnedCardIds)
            )) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "cannot resolve forgetting required: no forgettable cards (all are strengthened/weakened/locked or required by current deck)");
            }

            OwnedCard selectedCard = ownedCards.get(ownedCardIndexRaw);
            boolean inCurrentDeck = currentDeckOwnedCardIds.contains(selectedCard.ownedCardId());
            OwnedCardForgetPolicy.ForgetCheck forgetCheck = OwnedCardForgetPolicy.evaluateWithDeckMembership(
                    selectedCard,
                    ownedCounts,
                    deckCounts,
                    inCurrentDeck
            );
            if (!forgetCheck.forgettable()) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "cannot forget owned card at index " + ownedCardIndexRaw + ": " + forgetCheck.reason());
            }

            ownedCards.remove((int) ownedCardIndexRaw);
            ps.ownedCards(ownedCards);
            return state;
        });
    }

    private void loadDeck(GameState state, PlayerState ps, List<String> deckOwnedCardIds) {
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

    private void validateDeckBuild(List<String> deckOwnedCardIds, List<OwnedCard> ownedCards, List<String> currentDeckOwnedCardIds) {
        if (deckOwnedCardIds.size() != DECK_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "deck must contain exactly 12 cards");
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
                throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds must not contain duplicate values: " + normalizedOwnedCardId);
            }
            OwnedCard owned = ownedById.get(normalizedOwnedCardId);
            if (owned == null) {
                throw new ResponseStatusException(BAD_REQUEST, "owned card unavailable: " + normalizedOwnedCardId);
            }
            deckCardIds.add(owned.cardId());
        }

        Map<String, Integer> deckCounts = cardCounts(deckCardIds);
        for (var e : deckCounts.entrySet()) {
            if (e.getValue() > MAX_DECK_COPIES) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "card copy limit exceeded: " + e.getKey() + " (max 3)");
            }
        }

        if (currentDeckOwnedCardIds != null) {
            int changedCards = calculateDeckChangedCards(currentDeckOwnedCardIds, deckOwnedCardIds);
            if (changedCards > MAX_DECK_EDIT_CHANGES) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "deck edit invalid: at most 2 cards can be changed (requested " + changedCards + ")"
                );
            }

            Set<String> requiredLockedOwnedCardIds = lockedOwnedCardIdsRequiredInDeck(currentDeckOwnedCardIds, ownedCards);
            for (String requiredLockedOwnedCardId : requiredLockedOwnedCardIds) {
                if (!seenOwnedCardIds.contains(requiredLockedOwnedCardId)) {
                    throw new ResponseStatusException(
                            BAD_REQUEST,
                            "deck edit invalid: locked-in-deck card must remain in deck: " + requiredLockedOwnedCardId
                    );
                }
            }
        }
    }

    private void validateDeckEditableState(NodeState nodeState, PlayerState ps) {
        if (nodeState == NodeState.COMBAT) {
            throw new ResponseStatusException(FORBIDDEN, "deck edit unavailable during combat");
        }
        if (nodeState.curseBlocked()) {
            throw new ResponseStatusException(FORBIDDEN, "deck edit unavailable during curse");
        }
        if (!nodeState.deckEditable()) {
            throw new ResponseStatusException(FORBIDDEN, "deck cannot be edited in current node state: " + nodeState.name());
        }
        if (ps.forgettingRequired()) {
            throw new ResponseStatusException(FORBIDDEN,
                    "forgetting required: owned card limit exceeded ("
                            + ps.ownedCardCount()
                            + "/"
                            + ps.maxOwnedCardCount()
                            + ")");
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

    private Map<String, Integer> cardCountsFromOwned(List<OwnedCard> ownedCards) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            counts.merge(ownedCard.cardId(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> cardCounts(List<String> cardIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cardId : cardIds) {
            counts.merge(cardId, 1, Integer::sum);
        }
        return counts;
    }

    private List<String> currentDeckOwnedCardIds(PlayerState ps) {
        return ps.deckOwnedCardIds();
    }

    private List<String> currentDeckCardIdsFromOwnedCardIds(List<String> deckOwnedCardIds, List<OwnedCard> ownedCards) {
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

    private void persistCharacterDeck(SessionRuntime rt, PlayerId playerId, List<String> deckOwnedCardIds, List<OwnedCard> ownedCards) {
        Long characterId = rt.findCharacterIdByPlayerId(playerId.value());
        if (characterId == null) {
            return;
        }

        CharacterProfile profile = characterProfileRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "character not found: " + characterId));
        profile.setCurrentSkillDeck(List.copyOf(deckOwnedCardIds));
        characterProfileRepository.save(profile);
        deckService.upsertCharacterCurrentSkillDeck(characterId, currentDeckCardIdsFromOwnedCardIds(deckOwnedCardIds, ownedCards));
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
        List<String> currentSkillDeck = profile.getCurrentSkillDeck();
        String exCardId = parseExCardId(profile.getExCard());

        return new CharacterJoinTemplate(
                List.copyOf(passiveIds),
                currentSkillDeck == null ? null : List.copyOf(currentSkillDeck),
                exCardId,
                ownedCards
        );
    }

    private List<OwnedCardDto> parseOwnedCardsJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<JsonNode> nodes = JSON.readValue(raw, new TypeReference<>() {});
            List<OwnedCardDto> out = new ArrayList<>();
            for (JsonNode node : nodes) {
                if (node == null || node.isNull()) continue;
                if (node.isTextual()) {
                    String cardId = node.asText("").trim();
                    if (!cardId.isEmpty()) out.add(new OwnedCardDto(null, cardId, List.of(), false, false, false, true, null));
                    continue;
                }
                String cardId = node.path("cardId").asText("").trim();
                if (cardId.isEmpty()) continue;
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
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "character ownedCards JSON is invalid");
        }
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

    private record CharacterJoinTemplate(
            List<String> passiveIds,
            List<String> currentSkillDeck,
            String exCardId,
            List<OwnedCardDto> ownedCards
    ) {}

    private List<String> parsePassiveIds(List<String> passiveIdsRaw) {
        if (passiveIdsRaw == null) return List.of();
        if (passiveIdsRaw.size() > PlayerState.MAX_PASSIVES) {
            throw new ResponseStatusException(BAD_REQUEST, "passiveIds allows 0 to " + PlayerState.MAX_PASSIVES + " items.");
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

    private List<OwnedCard> parseOwnedCards(List<OwnedCardDto> ownedCardsRaw) {
        if (ownedCardsRaw == null || ownedCardsRaw.isEmpty()) {
            return defaultOwnedCards();
        }

        List<OwnedCard> out = new ArrayList<>(ownedCardsRaw.size());
        for (OwnedCardDto dto : ownedCardsRaw) {
            if (dto == null || dto.cardId() == null || dto.cardId().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "ownedCards.cardId is required");
            }
            String ownedCardId = (dto.ownedCardId() == null || dto.ownedCardId().isBlank()) ? UUID.randomUUID().toString() : dto.ownedCardId().trim();
            List<OwnedCardModifier> modifiers = toOwnedCardModifiers(dto);
            out.add(new OwnedCard(ownedCardId, dto.cardId().trim(), modifiers));
        }
        return List.copyOf(out);
    }

    private List<OwnedCardModifier> toOwnedCardModifiers(OwnedCardDto dto) {
        List<OwnedCardModifier> out = new ArrayList<>();
        if (dto.modifiers() != null) {
            for (OwnedCardModifierDto modifierDto : dto.modifiers()) {
                if (modifierDto == null || modifierDto.modifierId() == null || modifierDto.modifierId().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "ownedCards.modifiers.modifierId is required");
                }
                out.add(new OwnedCardModifier(modifierDto.modifierId().trim(), modifierDto.value() == null ? 0 : modifierDto.value()));
            }
        }
        // Legacy boolean flags are compatibility-only input shims; modifiers are canonical.
        if (Boolean.TRUE.equals(dto.strengthened()) && out.stream().noneMatch(m -> CardModifierIds.STRENGTHENED.equals(m.modifierId()))) {
            out.add(new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1));
        }
        if (Boolean.TRUE.equals(dto.weakened())
                && out.stream().noneMatch(m -> CardModifierIds.WEAKENED.equals(m.modifierId()))
                && !OwnedCardModifierSemantics.hasConcreteWeakenedModifier(out)) {
            out.add(new OwnedCardModifier(CardModifierIds.WEAKENED, 1));
        }
        if (Boolean.TRUE.equals(dto.lockedInDeck()) && out.stream().noneMatch(m -> CardModifierIds.LOCKED_IN_DECK.equals(m.modifierId()))) {
            out.add(new OwnedCardModifier(CardModifierIds.LOCKED_IN_DECK, 1));
        }
        return List.copyOf(out);
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

    private List<String> resolveJoinDeckOwnedCardIds(CharacterJoinTemplate characterTemplate,
                                                    List<String> requestedPresetDeckOwnedCardIdsRaw,
                                                    List<OwnedCard> ownedCards) {
        if (characterTemplate != null) {
            List<String> fromProfile = resolveStoredDeckToOwnedCardIds(characterTemplate.currentSkillDeck(), ownedCards);
            if (fromProfile != null) {
                return fromProfile;
            }
            return resolveCardIdsToOwnedCardIds(defaultPresetDeckCardIds(), ownedCards, "deckCardIds must not contain blank values");
        }

        if (requestedPresetDeckOwnedCardIdsRaw != null) {
            return resolveStoredDeckToOwnedCardIds(requestedPresetDeckOwnedCardIdsRaw, ownedCards);
        }
        return resolveCardIdsToOwnedCardIds(defaultPresetDeckCardIds(), ownedCards, "deckCardIds must not contain blank values");
    }

    private List<String> normalizeDeckOwnedCardIds(List<String> deckOwnedCardIdsRaw) {
        if (deckOwnedCardIdsRaw != null) {
            List<String> normalized = new ArrayList<>();
            for (String ownedCardId : deckOwnedCardIdsRaw) {
                if (ownedCardId == null || ownedCardId.isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds must not contain blank values");
                }
                normalized.add(ownedCardId.trim());
            }
            return List.copyOf(normalized);
        }
        throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds is required");
    }

    private List<String> resolveStoredDeckToOwnedCardIds(List<String> storedDeckEntries, List<OwnedCard> ownedCards) {
        if (storedDeckEntries == null) {
            return null;
        }
        Map<String, OwnedCard> ownedById = ownedCardMap(ownedCards);
        boolean allOwnedCardIds = true;
        for (String entry : storedDeckEntries) {
            if (entry == null || entry.isBlank() || !ownedById.containsKey(entry.trim())) {
                allOwnedCardIds = false;
                break;
            }
        }
        if (allOwnedCardIds) {
            return normalizeDeckOwnedCardIds(storedDeckEntries);
        }
        return resolveCardIdsToOwnedCardIds(storedDeckEntries, ownedCards, "deckCardIds must not contain blank values");
    }

    private List<String> resolveCardIdsToOwnedCardIds(List<String> cardIdsRaw,
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

    private Map<String, OwnedCard> ownedCardMap(List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            out.put(ownedCard.ownedCardId(), ownedCard);
        }
        return out;
    }

    private String normalizeExCardId(String raw) {
        return (raw == null || raw.isBlank()) ? "EX901" : raw.trim();
    }

    private List<OwnedCard> defaultOwnedCards() {
        List<OwnedCard> owned = new ArrayList<>(20);
        for (int i = 0; i < 5; i++) owned.add(OwnedCard.fromLegacy("C001", false, false, false));
        for (int i = 0; i < 5; i++) owned.add(OwnedCard.fromLegacy("C002", false, false, false));
        for (int i = 0; i < 5; i++) owned.add(OwnedCard.fromLegacy("C003", false, false, false));
        for (int i = 0; i < 5; i++) owned.add(OwnedCard.fromLegacy("C004", false, false, false));
        return List.copyOf(owned);
    }

    private List<String> defaultPresetDeckCardIds() {
        return List.of(
                "C001", "C001", "C001",
                "C002", "C002", "C002",
                "C003", "C003", "C003",
                "C004", "C004", "C004"
        );
    }

    private void addCardToDeck(GameState state, PlayerState ps, CardDefId defId) {
        addCardToDeck(state, ps, defId, null, List.of());
    }

    private void addCardToDeck(GameState state,
                               PlayerState ps,
                               CardDefId defId,
                               String sourceOwnedCardId,
                               List<OwnedCardModifier> modifiers) {
        ZoneOps.createCardInZone(state, ps, defId, Zone.DECK, sourceOwnedCardId, modifiers);
    }

    private void addCardToEx(GameState state, PlayerState ps, CardDefId defId) {
        CardInstId previousEx = ps.exCard();
        if (previousEx != null) {
            state.cardInstances().remove(previousEx);
            ps.exCard(null);
        }

        ZoneOps.createCardInZone(state, ps, defId, Zone.EX);
    }

    private void shuffleDeck(GameState state, PlayerState ps) {
        List<CardInstId> list = new ArrayList<>(ps.deck());
        ps.deck().clear();
        Collections.shuffle(list, new Random(state.seed() ^ ps.playerId().value().hashCode()));
        for (CardInstId id : list) ps.deck().addLast(id);
    }

    private String generateCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)]);
        return sb.toString();
    }

    private String generateGmToken() {
        byte[] bytes = new byte[32];
        rnd.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Scheduled(fixedDelayString = "${duel.session.cleanup-interval:5m}")
    public void cleanupExpiredSessions() {
        evictExpiredSessions();
    }

    private void evictExpiredSessions() {
        Instant now = Instant.now();
        int removed = 0;

        for (Map.Entry<String, SessionRuntime> entry : sessions.entrySet()) {
            SessionRuntime rt = entry.getValue();
            Instant expirationBoundary = rt.lastAccessedAt().plus(sessionTtl);
            if (expirationBoundary.isAfter(now)) {
                continue;
            }

            if (sessions.remove(entry.getKey(), rt)) {
                removed++;
            }
        }

        if (removed > 0) {
            log.info("expired session cleanup removed={} ttl={} interval={}", removed, sessionTtl, cleanupInterval);
        }
    }

    private boolean isExpired(SessionRuntime rt) {
        return !rt.lastAccessedAt().plus(sessionTtl).isAfter(Instant.now());
    }
}
