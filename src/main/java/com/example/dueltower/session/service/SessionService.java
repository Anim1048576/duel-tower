package com.example.dueltower.session.service;

import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.deck.service.DeckService;
import com.example.dueltower.content.cardmodifier.service.CardModifierService;
import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.item.service.ItemService;
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
import com.example.dueltower.common.api.ApiErrorException;
import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.session.config.StarterLoadoutConfig;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.OwnedCardModifierDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.preset.service.PresetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
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

    private static final Pattern PASSIVE_ID_FORMAT = Pattern.compile("^P\\d{3}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CharacterProfileRepository characterProfileRepository;
    private final CardService cardService;
    private final DeckService deckService;
    private final StatusService statusService;
    private final KeywordService keywordService;
    private final ItemService itemService;
    private final EquipService equipService;
    private final PassiveService passiveService;
    private final CardModifierService cardModifierService;
    private final PresetService presetService;
    private final Duration sessionTtl;
    private final Duration cleanupInterval;
    private final GameRules gameRules;
    private final RewardTableConfig rewardTableConfig;
    private final StarterLoadoutConfig starterLoadoutConfig;

    // code -> runtime (in-memory)
    private final Map<String, SessionRuntime> sessions = new ConcurrentHashMap<>();

    private final SecureRandom rnd = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    public SessionService(CharacterProfileRepository characterProfileRepository,
                          CardService cardService,
                          DeckService deckService,
                          StatusService statusService,
                          KeywordService keywordService,
                          ItemService itemService,
                          EquipService equipService,
                          PassiveService passiveService,
                          CardModifierService cardModifierService,
                          PresetService presetService,
                          Duration sessionTtl,
                          Duration cleanupInterval) {
        this(characterProfileRepository, cardService, deckService, statusService, keywordService, itemService, equipService,
                passiveService, cardModifierService, presetService,
                GameRules.defaults(),
                RewardTableConfig.defaults(),
                StarterLoadoutConfig.defaults(GameRules.defaults()),
                sessionTtl,
                cleanupInterval);
    }

    @Autowired
    public SessionService(CharacterProfileRepository characterProfileRepository,
                          CardService cardService,
                          DeckService deckService,
                          StatusService statusService,
                          KeywordService keywordService,
                          ItemService itemService,
                          EquipService equipService,
                          PassiveService passiveService,
                          CardModifierService cardModifierService,
                          PresetService presetService,
                          GameRules gameRules,
                          RewardTableConfig rewardTableConfig,
                          StarterLoadoutConfig starterLoadoutConfig,
                          @Value("${duel.session.ttl:30m}") Duration sessionTtl,
                          @Value("${duel.session.cleanup-interval:5m}") Duration cleanupInterval) {
        this.characterProfileRepository = characterProfileRepository;
        this.cardService = cardService;
        this.deckService = deckService;
        this.statusService = statusService;
        this.keywordService = keywordService;
        this.itemService = itemService;
        this.equipService = equipService;
        this.passiveService = passiveService;
        this.cardModifierService = cardModifierService;
        this.presetService = presetService;
        this.sessionTtl = sessionTtl;
        this.cleanupInterval = cleanupInterval;
        this.gameRules = gameRules;
        this.rewardTableConfig = rewardTableConfig;
        this.starterLoadoutConfig = starterLoadoutConfig;
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
                    cardModifierService.effectsMap(),
                    itemService.defsMap(),
                    itemService.effectsMap(),
                    equipService.defsMap(),
                    gameRules,
                    rewardTableConfig
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

    public GameState leaveSession(String code, String actorPlayerIdRaw) {
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        SessionRuntime rt = get(code);
        return rt.withLock(() -> removePlayerFromSession(rt, actor, "player not found"));
    }

    public GameState setPlayerReady(String code, String actorPlayerIdRaw, String targetPlayerIdRaw, boolean ready) {
        if (actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "actorPlayerId is required");
        }
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        PlayerId actor = new PlayerId(actorPlayerIdRaw.trim());
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());
        if (!actor.equals(target)) {
            throw new ResponseStatusException(FORBIDDEN, "players may only update their own ready state");
        }

        SessionRuntime rt = get(code);
        return rt.withLock(() -> {
            PlayerState player = rt.state().players().get(target);
            if (player == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }
            player.ready(ready);
            return rt.state();
        });
    }

    public GameState kickPlayer(String code, String targetPlayerIdRaw) {
        if (targetPlayerIdRaw == null || targetPlayerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }
        PlayerId target = new PlayerId(targetPlayerIdRaw.trim());
        SessionRuntime rt = get(code);
        return rt.withLock(() -> removePlayerFromSession(rt, target, "player not found"));
    }

    public GameState resetSession(String code, boolean keepPlayers, boolean keepLoadouts, Long newSeed) {
        SessionRuntime rt = get(code);
        return rt.withLock(() -> {
            GameState state = rt.state();
            long resetSeed = (newSeed == null) ? state.seed() : newSeed;
            resetSessionState(rt, state, keepPlayers, keepLoadouts, resetSeed);
            return state;
        });
    }

    public void deleteSession(String code) {
        SessionRuntime rt = sessions.remove(code);
        if (rt == null) {
            throw new ResponseStatusException(NOT_FOUND, "session not found");
        }
    }

    public GameState updateDeck(String code,
                                String actorPlayerIdRaw,
                                String targetPlayerIdRaw,
                                List<String> deckOwnedCardIdsRaw) {
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

            List<String> deckOwnedCardIds = resolveRequestedDeckOwnedCardIds(deckOwnedCardIdsRaw, ps.ownedCards());
            validateDeckBuild(deckOwnedCardIds, ps.ownedCards(), currentDeckOwnedCardIds(ps));
            ps.deckOwnedCardIds(deckOwnedCardIds);
            loadDeck(state, ps, deckOwnedCardIds);
            shuffleDeck(state, ps);
            persistCharacterDeck(rt, target, deckOwnedCardIds, ps.ownedCards());
            return state;
        });
    }

    public GameState updateLoadout(String code,
                                   String actorPlayerIdRaw,
                                   String targetPlayerIdRaw,
                                   Long characterIdRaw,
                                   List<String> passiveIdsRaw,
                                   List<String> deckOwnedCardIdsRaw,
                                   String exCardIdRaw) {
        return applyLoadoutToPlayer(
                code,
                actorPlayerIdRaw,
                targetPlayerIdRaw,
                LoadoutApplySpec.direct(characterIdRaw, passiveIdsRaw, deckOwnedCardIdsRaw, exCardIdRaw)
        );
    }

    public GameState applyPresetToLoadout(String code,
                                          String actorPlayerIdRaw,
                                          String targetPlayerIdRaw,
                                          Long presetIdRaw) {
        if (presetIdRaw == null || presetIdRaw <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "presetId must be positive");
        }
        String ownerUsername = requirePlayerText(actorPlayerIdRaw, "actorPlayerId is required");
        PresetService.PresetLoadout presetLoadout = presetService.getOwnedLoadout(ownerUsername, presetIdRaw);
        return applyLoadoutToPlayer(
                code,
                actorPlayerIdRaw,
                targetPlayerIdRaw,
                LoadoutApplySpec.fromPreset(presetLoadout)
        );
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
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "INVALID_OWNED_CARD_INDEX",
                        "VALIDATION",
                        "선택한 카드 위치를 다시 확인해 주세요.",
                        "ownedCardIndex out of range: " + ownedCardIndexRaw + " (size " + ownedCards.size() + ")",
                        ApiErrorResolver.details("ownedCardIndex", ownedCardIndexRaw, "ownedCardCount", ownedCards.size())
                );
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
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "FORGET_REQUIRED",
                        "RULE",
                        "먼저 잊을 수 있는 카드를 정리해야 다음 단계로 진행할 수 있습니다.",
                        "cannot resolve forgetting required: no forgettable cards (all are strengthened/weakened/locked or required by current deck)",
                        ApiErrorResolver.details("playerId", target.value(), "ownedCardCount", ownedCards.size())
                );
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
                throw new ApiErrorException(
                        BAD_REQUEST,
                        "CARD_NOT_FORGETTABLE",
                        "RULE",
                        "선택한 카드는 지금 잊을 수 없습니다.",
                        "cannot forget owned card at index " + ownedCardIndexRaw + ": " + forgetCheck.reason(),
                        ApiErrorResolver.details("ownedCardIndex", ownedCardIndexRaw, "reason", forgetCheck.reason())
                );
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
        if (deckOwnedCardIds.size() != gameRules.deckSize()) {
            throw new ApiErrorException(
                    BAD_REQUEST,
                    "DECK_EDIT_INVALID",
                    "VALIDATION",
                    "덱은 정확히 " + gameRules.deckSize() + "장으로 맞춰야 합니다.",
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
                        "덱에 같은 보유 사본을 중복해서 넣을 수 없습니다.",
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
                        "선택한 카드 사본을 현재 보유 목록에서 찾을 수 없습니다.",
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
                        "같은 카드는 덱에 최대 " + gameRules.maxDeckCopies() + "장까지 넣을 수 있습니다.",
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
                        "현재 덱에서는 최대 " + gameRules.maxDeckEditChanges() + "장까지만 교체할 수 있습니다.",
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

    private void validateDeckEditableState(NodeState nodeState, PlayerState ps) {
        if (nodeState == NodeState.COMBAT) {
            throw new ApiErrorException(FORBIDDEN, "DECK_EDIT_FORBIDDEN", "RULE", "전투 중에는 덱을 수정할 수 없습니다.", "deck edit unavailable during combat", null);
        }
        if (nodeState.curseBlocked()) {
            throw new ApiErrorException(FORBIDDEN, "DECK_EDIT_FORBIDDEN", "RULE", "현재 저주 상태에서는 덱을 수정할 수 없습니다.", "deck edit unavailable during curse", null);
        }
        if (!nodeState.deckEditable()) {
            throw new ApiErrorException(FORBIDDEN, "DECK_EDIT_FORBIDDEN", "RULE", "현재 노드 상태에서는 덱을 수정할 수 없습니다.", "deck cannot be edited in current node state: " + nodeState.name(), ApiErrorResolver.details("nodeState", nodeState.name()));
        }
        if (ps.forgettingRequired()) {
            throw new ApiErrorException(
                    FORBIDDEN,
                    "FORGET_REQUIRED",
                    "RULE",
                    "카드 잊기 상태를 먼저 해결해야 덱을 수정할 수 있습니다.",
                    "forgetting required: owned card limit exceeded ("
                            + ps.ownedCardCount()
                            + "/"
                            + ps.maxOwnedCardCount()
                            + ")",
                    ApiErrorResolver.details("ownedCardCount", ps.ownedCardCount(), "maxOwnedCardCount", ps.maxOwnedCardCount())
            );
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

        List<String> deckCardIds = currentDeckCardIdsFromOwnedCardIds(deckOwnedCardIds, ownedCards);
        profile.setOwnedCards(toCanonicalOwnedCardsJson(ownedCards));
        profile.setCurrentSkillDeck(List.copyOf(deckOwnedCardIds));
        characterProfileRepository.save(profile);

        deckService.upsertCharacterCurrentSkillDeck(characterId, deckCardIds);
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

    private List<OwnedCardDto> parseOwnedCardsJson(String raw) {
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

    private record CharacterJoinTemplate(
            List<String> passiveIds,
            List<String> currentSkillDeck,
            String exCardId,
            List<OwnedCardDto> ownedCards
    ) {}

    private record LoadoutApplySpec(
            Long characterId,
            List<String> passiveIds,
            List<String> deckCardIds,
            String exCardId
    ) {
        private static LoadoutApplySpec direct(Long characterId,
                                               List<String> passiveIds,
                                               List<String> deckCardIds,
                                               String exCardId) {
            return new LoadoutApplySpec(characterId, passiveIds, deckCardIds, exCardId);
        }

        private static LoadoutApplySpec fromPreset(PresetService.PresetLoadout presetLoadout) {
            return new LoadoutApplySpec(
                    presetLoadout.characterId(),
                    presetLoadout.passiveIds(),
                    presetLoadout.deckCardIds(),
                    presetLoadout.exCardId()
            );
        }
    }

    private List<String> parsePassiveIds(List<String> passiveIdsRaw) {
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

    private List<OwnedCard> parseOwnedCards(List<OwnedCardDto> ownedCardsRaw) {
        if (ownedCardsRaw == null || ownedCardsRaw.isEmpty()) {
            return starterLoadoutConfig.defaultOwnedCards();
        }
        return SessionNormalizationSupport.normalizeOwnedCards(ownedCardsRaw);
    }

    private List<OwnedCardModifier> toOwnedCardModifiers(OwnedCardDto dto) {
        return SessionNormalizationSupport.normalizeOwnedCardModifiers(dto);
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
            List<String> currentSkillDeck = characterTemplate.currentSkillDeck();
            if (currentSkillDeck == null || currentSkillDeck.isEmpty()) {
                return List.of();
            }
            return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(currentSkillDeck, ownedCards);
        }

        if (requestedPresetDeckOwnedCardIdsRaw != null) {
            return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(requestedPresetDeckOwnedCardIdsRaw, ownedCards);
        }

        return List.of();
    }

    private List<String> resolveRequestedDeckOwnedCardIds(List<String> deckOwnedCardIdsRaw, List<OwnedCard> ownedCards) {
        if (deckOwnedCardIdsRaw != null) {
            return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(deckOwnedCardIdsRaw, ownedCards);
        }
        throw new ResponseStatusException(BAD_REQUEST, "deckOwnedCardIds is required");
    }

    private List<String> resolveStoredDeckToOwnedCardIds(List<String> storedDeckEntries, List<OwnedCard> ownedCards) {
        return SessionNormalizationSupport.normalizeStoredOrRequestedDeckToOwnedCardIds(storedDeckEntries, ownedCards);
    }

    private void applyLoadout(SessionRuntime rt,
                              GameState state,
                              PlayerState ps,
                              CharacterJoinTemplate characterTemplate,
                              Long characterIdRaw,
                              List<String> passiveIdsRaw,
                              List<String> deckOwnedCardIdsRaw,
                              String exCardIdRaw) {
        List<String> previousDeckOwnedCardIds = currentDeckOwnedCardIds(ps);
        List<OwnedCard> effectiveOwnedCards = resolveLoadoutOwnedCards(ps, characterTemplate);
        List<String> effectivePassiveIds = resolveLoadoutPassiveIds(ps, characterTemplate, passiveIdsRaw);
        List<String> effectiveDeckOwnedCardIds = resolveLoadoutDeckOwnedCardIds(ps, effectiveOwnedCards, characterTemplate, deckOwnedCardIdsRaw);
        String effectiveExCardId = resolveLoadoutExCardId(state, ps, characterTemplate, exCardIdRaw);

        List<String> currentDeckForValidation = (characterTemplate == null) ? previousDeckOwnedCardIds : null;
        boolean allowEmptyCharacterDeck = characterTemplate != null && effectiveDeckOwnedCardIds.isEmpty();
        if (!allowEmptyCharacterDeck) {
            validateDeckBuild(effectiveDeckOwnedCardIds, effectiveOwnedCards, currentDeckForValidation);
        }
        validateExCardId(effectiveExCardId);

        if (characterTemplate != null && characterIdRaw != null) {
            rt.bindCharacterId(ps.playerId().value(), characterIdRaw);
        }

        ps.ownedCards(effectiveOwnedCards);
        ps.passiveIds(effectivePassiveIds);
        ps.deckOwnedCardIds(effectiveDeckOwnedCardIds);
        loadDeck(state, ps, effectiveDeckOwnedCardIds);
        addCardToEx(state, ps, new CardDefId(normalizeExCardId(effectiveExCardId)));
        shuffleDeck(state, ps);
    }

    /**
     * loadout 반영 단일 경로.
     * - 일반 업데이트 / preset 적용 모두 동일한 검증/반영 흐름으로 처리한다.
     * - 다음 단계에서 clone preset 등 확장 시 LoadoutApplySpec 변환만 추가하면 된다.
     */
    private GameState applyLoadoutToPlayer(String code,
                                           String actorPlayerIdRaw,
                                           String targetPlayerIdRaw,
                                           LoadoutApplySpec spec) {
        String actorPlayerId = requirePlayerText(actorPlayerIdRaw, "actorPlayerId is required");
        String targetPlayerId = requirePlayerText(targetPlayerIdRaw, "playerId is required");

        PlayerId actor = new PlayerId(actorPlayerId);
        PlayerId target = new PlayerId(targetPlayerId);
        SessionRuntime rt = get(code);
        return rt.withLock(() -> {
            GameState state = rt.state();
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only edit their own loadout");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            validateDeckEditableState(state.nodeState(), ps);
            CharacterJoinTemplate characterTemplate = (spec.characterId() == null)
                    ? null
                    : toCharacterJoinTemplate(loadCharacterProfile(spec.characterId()));

            applyLoadout(
                    rt,
                    state,
                    ps,
                    characterTemplate,
                    spec.characterId(),
                    spec.passiveIds(),
                    spec.deckCardIds(),
                    spec.exCardId()
            );
            persistCharacterDeck(rt, target, ps.deckOwnedCardIds(), ps.ownedCards());
            return state;
        });
    }

    private String requirePlayerText(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return raw.trim();
    }

    private List<OwnedCard> resolveLoadoutOwnedCards(PlayerState ps, CharacterJoinTemplate characterTemplate) {
        if (characterTemplate == null) {
            return List.copyOf(ps.ownedCards());
        }
        return parseOwnedCards(characterTemplate.ownedCards());
    }

    private List<String> resolveLoadoutPassiveIds(PlayerState ps,
                                                  CharacterJoinTemplate characterTemplate,
                                                  List<String> passiveIdsRaw) {
        if (passiveIdsRaw != null) {
            return parsePassiveIds(passiveIdsRaw);
        }
        if (characterTemplate != null) {
            return parsePassiveIds(characterTemplate.passiveIds());
        }
        return List.copyOf(ps.passiveIds());
    }

    private List<String> resolveLoadoutDeckOwnedCardIds(PlayerState ps,
                                                        List<OwnedCard> effectiveOwnedCards,
                                                        CharacterJoinTemplate characterTemplate,
                                                        List<String> deckOwnedCardIdsRaw) {
        if (deckOwnedCardIdsRaw != null) {
            return resolveRequestedDeckOwnedCardIds(deckOwnedCardIdsRaw, effectiveOwnedCards);
        }
        if (characterTemplate != null) {
            List<String> currentSkillDeck = characterTemplate.currentSkillDeck();
            if (currentSkillDeck == null || currentSkillDeck.isEmpty()) {
                return List.of();
            }
            return resolveStoredDeckToOwnedCardIds(currentSkillDeck, effectiveOwnedCards);
        }
        return List.copyOf(ps.deckOwnedCardIds());
    }

    private String resolveLoadoutExCardId(GameState state,
                                          PlayerState ps,
                                          CharacterJoinTemplate characterTemplate,
                                          String exCardIdRaw) {
        if (exCardIdRaw != null) {
            return normalizeExCardId(exCardIdRaw);
        }
        if (characterTemplate != null) {
            return normalizeExCardId(characterTemplate.exCardId());
        }
        return normalizeExCardId(resolveCurrentExCardId(state, ps));
    }

    private void validateExCardId(String exCardIdRaw) {
        String exCardId = normalizeExCardId(exCardIdRaw);
        if (!cardService.asMap().containsKey(new CardDefId(exCardId))) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid exCardId: " + exCardId);
        }
    }

    private GameState removePlayerFromSession(SessionRuntime rt, PlayerId target, String notFoundMessage) {
        GameState state = rt.state();
        if (state.nodeState() == NodeState.COMBAT) {
            throw new ResponseStatusException(FORBIDDEN, "player management is unavailable during combat");
        }

        PlayerState ps = state.player(target);
        if (ps == null) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        removePlayerRuntimeState(state, ps);
        state.players().remove(target);
        rt.removePlayerBindings(target.value());
        return state;
    }

    private void resetSessionState(SessionRuntime rt,
                                   GameState state,
                                   boolean keepPlayers,
                                   boolean keepLoadouts,
                                   long resetSeed) {
        Map<PlayerId, String> exCardByPlayerId = new LinkedHashMap<>();
        if (keepPlayers && keepLoadouts) {
            for (Map.Entry<PlayerId, PlayerState> entry : state.players().entrySet()) {
                exCardByPlayerId.put(entry.getKey(), resolveCurrentExCardId(state, entry.getValue()));
            }
        }

        state.enemies().clear();
        state.summons().clear();
        state.cardInstances().clear();
        state.combat(null);
        state.nodeState(NodeState.NON_COMBAT);

        if (!keepPlayers) {
            state.players().clear();
            rt.clearPlayerBindings();
            state.runState().initialize(resetSeed);
            return;
        }

        for (PlayerState ps : state.players().values()) {
            String exCardId = exCardByPlayerId.getOrDefault(ps.playerId(), starterLoadoutConfig.defaultExCardId());
            resetPlayerState(rt, state, ps, keepLoadouts, resetSeed, exCardId);
        }
        state.runState().initialize(resetSeed);
    }

    private void resetPlayerState(SessionRuntime rt,
                                  GameState state,
                                  PlayerState ps,
                                  boolean keepLoadouts,
                                  long resetSeed,
                                  String preservedExCardId) {
        ps.deck().clear();
        ps.hand().clear();
        ps.grave().clear();
        ps.field().clear();
        ps.excluded().clear();
        ps.activeSummons().clear();
        ps.summonByCard().clear();
        ps.statusValues().clear();
        ps.pendingDecision(null);
        ps.swappedThisTurn(false);
        ps.cardsPlayedThisTurn(0);
        ps.usedExThisTurn(false);
        ps.usedTenacityThisTurn(false);
        ps.tenacityDebtThisTurn(0);
        ps.exCooldownUntilRound(0);
        ps.exActivatable(true);
        ps.refillToMax();
        ps.exCard(null);

        if (!keepLoadouts) {
            List<OwnedCard> defaultOwnedCards = starterLoadoutConfig.defaultOwnedCards();
            ps.passiveIds(List.of());
            ps.ownedCards(defaultOwnedCards);
            List<String> defaultDeckOwnedCardIds = resolveCardIdsToOwnedCardIds(
                    starterLoadoutConfig.defaultPresetDeckCardIds(),
                    defaultOwnedCards,
                    "deckCardIds must not contain blank values"
            );
            ps.deckOwnedCardIds(defaultDeckOwnedCardIds);
            loadDeck(state, ps, defaultDeckOwnedCardIds);
            addCardToEx(state, ps, new CardDefId(starterLoadoutConfig.defaultExCardId()));
            rt.clearCharacterBinding(ps.playerId().value());
            shuffleDeck(state, ps, resetSeed);
            return;
        }

        List<String> deckOwnedCardIds = currentDeckOwnedCardIds(ps);
        if (deckOwnedCardIds != null && !deckOwnedCardIds.isEmpty()) {
            loadDeck(state, ps, deckOwnedCardIds);
            shuffleDeck(state, ps, resetSeed);
        }
        addCardToEx(state, ps, new CardDefId((preservedExCardId == null || preservedExCardId.isBlank())
                ? starterLoadoutConfig.defaultExCardId()
                : preservedExCardId.trim()));
    }

    private void removePlayerRuntimeState(GameState state, PlayerState ps) {
        Set<CardInstId> toDelete = new LinkedHashSet<>();
        toDelete.addAll(ps.deck());
        toDelete.addAll(ps.hand());
        toDelete.addAll(ps.grave());
        toDelete.addAll(ps.field());
        toDelete.addAll(ps.excluded());
        if (ps.exCard() != null) {
            toDelete.add(ps.exCard());
        }
        for (CardInstId id : toDelete) {
            state.cardInstances().remove(id);
        }

        List<Ids.SummonInstId> summonIds = new ArrayList<>(ps.activeSummons());
        for (Ids.SummonInstId summonId : summonIds) {
            state.summons().remove(summonId);
        }
    }

    private String resolveCurrentExCardId(GameState state, PlayerState ps) {
        if (ps.exCard() == null) {
            return starterLoadoutConfig.defaultExCardId();
        }
        CardInstance exInst = state.card(ps.exCard());
        if (exInst == null || exInst.defId() == null || exInst.defId().value() == null || exInst.defId().value().isBlank()) {
            return starterLoadoutConfig.defaultExCardId();
        }
        return exInst.defId().value().trim();
    }

    private List<String> resolveCardIdsToOwnedCardIds(List<String> cardIdsRaw,
                                                  List<OwnedCard> ownedCards,
                                                  String blankValueMessage) {
        return SessionNormalizationSupport.resolveCardIdsToOwnedCardIds(cardIdsRaw, ownedCards, blankValueMessage);
    }

    private Map<String, OwnedCard> ownedCardMap(List<OwnedCard> ownedCards) {
        Map<String, OwnedCard> out = new LinkedHashMap<>();
        for (OwnedCard ownedCard : ownedCards) {
            out.put(ownedCard.ownedCardId(), ownedCard);
        }
        return out;
    }

    private String normalizeExCardId(String raw) {
        return (raw == null || raw.isBlank()) ? starterLoadoutConfig.defaultExCardId() : raw.trim();
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
        shuffleDeck(state, ps, state.seed());
    }

    private void shuffleDeck(GameState state, PlayerState ps, long seed) {
        List<CardInstId> list = new ArrayList<>(ps.deck());
        ps.deck().clear();
        Collections.shuffle(list, new Random(seed ^ ps.playerId().value().hashCode()));
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
