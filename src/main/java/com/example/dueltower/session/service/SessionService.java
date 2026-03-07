package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;
import com.example.dueltower.content.card.service.CardService;
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
import com.example.dueltower.session.runtime.SessionRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    private final CardService cardService;
    private final StatusService statusService;
    private final KeywordService keywordService;
    private final PassiveService passiveService;
    private final Duration sessionTtl;
    private final Duration cleanupInterval;

    // code -> runtime (in-memory)
    private final Map<String, SessionRuntime> sessions = new ConcurrentHashMap<>();

    private final SecureRandom rnd = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    public SessionService(CardService cardService,
                          StatusService statusService,
                          KeywordService keywordService,
                          PassiveService passiveService,
                          @Value("${duel.session.ttl:30m}") Duration sessionTtl,
                          @Value("${duel.session.cleanup-interval:5m}") Duration cleanupInterval) {
        this.cardService = cardService;
        this.statusService = statusService;
        this.keywordService = keywordService;
        this.passiveService = passiveService;
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
                    passiveService.effectsMap()
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
                          List<String> passiveIdsRaw,
                          List<String> presetDeckCardIdsRaw,
                          String presetExCardIdRaw,
                          List<OwnedCardDto> ownedCardsRaw) {
        if (playerIdRaw == null || playerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        SessionRuntime rt = get(code);
        PlayerId pid = new PlayerId(playerIdRaw.trim());
        List<String> passiveIds = parsePassiveIds(passiveIdsRaw);

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

            List<OwnedCard> ownedCards = parseOwnedCards(ownedCardsRaw);
            ps.ownedCards(ownedCards);

            List<String> deckCardIds = parsePresetDeckCardIds(presetDeckCardIdsRaw);
            validateDeckBuild(deckCardIds, ps.ownedCards(), null);

            state.players().put(pid, ps);
            loadDeck(state, ps, deckCardIds);
            addCardToEx(state, ps, new CardDefId(normalizeExCardId(presetExCardIdRaw)));

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
                                List<String> deckCardIdsRaw) {
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
            validateDeckEditableState(state.nodeState());
            if (!actor.equals(target)) {
                throw new ResponseStatusException(FORBIDDEN, "players may only edit their own deck");
            }

            PlayerState ps = state.player(target);
            if (ps == null) {
                throw new ResponseStatusException(NOT_FOUND, "player not found");
            }

            List<String> deckCardIds = normalizeDeckCardIds(deckCardIdsRaw);
            validateDeckBuild(deckCardIds, ps.ownedCards(), currentDeckCardIds(ps, state));
            loadDeck(state, ps, deckCardIds);
            shuffleDeck(state, ps);
            return state;
        });
    }

    private void loadDeck(GameState state, PlayerState ps, List<String> deckCardIds) {
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

        for (String cardId : deckCardIds) {
            addCardToDeck(state, ps, new CardDefId(cardId));
        }
    }

    private void validateDeckBuild(List<String> deckCardIds, List<OwnedCard> ownedCards, List<String> currentDeckCardIds) {
        if (deckCardIds.size() != DECK_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "deck must contain exactly 12 cards");
        }

        Map<String, Integer> deckCounts = cardCounts(deckCardIds);
        for (var e : deckCounts.entrySet()) {
            if (e.getValue() > MAX_DECK_COPIES) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "card copy limit exceeded: " + e.getKey() + " (max 3)");
            }
        }

        Map<String, Integer> availableOwned = new LinkedHashMap<>();
        for (OwnedCard owned : ownedCards) {
            availableOwned.merge(owned.cardId(), 1, Integer::sum);
        }

        for (var e : deckCounts.entrySet()) {
            int available = availableOwned.getOrDefault(e.getKey(), 0);
            if (available < e.getValue()) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "owned card unavailable: " + e.getKey());
            }
        }

        if (currentDeckCardIds != null) {
            Map<String, Integer> currentDeckCounts = cardCounts(currentDeckCardIds);
            int changedCards = calculateDeckChangedCards(currentDeckCounts, deckCounts);
            if (changedCards > MAX_DECK_EDIT_CHANGES) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "deck edit invalid: at most 2 cards can be changed (requested " + changedCards + ")"
                );
            }

            Map<String, Integer> lockedCounts = lockedCardCountsInCurrentDeck(currentDeckCardIds, ownedCards);
            for (var e : lockedCounts.entrySet()) {
                int updatedCount = deckCounts.getOrDefault(e.getKey(), 0);
                if (updatedCount < e.getValue()) {
                    throw new ResponseStatusException(
                            BAD_REQUEST,
                            "deck edit invalid: locked-in-deck card must remain in deck: "
                                    + e.getKey()
                                    + " (required " + e.getValue() + ", provided " + updatedCount + ")"
                    );
                }
            }
        }
    }

    private void validateDeckEditableState(NodeState nodeState) {
        if (nodeState == NodeState.COMBAT) {
            throw new ResponseStatusException(FORBIDDEN, "deck edit unavailable during combat");
        }
        if (nodeState.curseBlocked()) {
            throw new ResponseStatusException(FORBIDDEN, "deck edit unavailable during curse");
        }
        if (!nodeState.deckEditable()) {
            throw new ResponseStatusException(FORBIDDEN, "deck cannot be edited in current node state: " + nodeState.name());
        }
    }

    private int calculateDeckChangedCards(Map<String, Integer> currentDeckCounts, Map<String, Integer> newDeckCounts) {
        int changed = 0;
        Set<String> union = new LinkedHashSet<>();
        union.addAll(currentDeckCounts.keySet());
        union.addAll(newDeckCounts.keySet());
        for (String cardId : union) {
            int currentCount = currentDeckCounts.getOrDefault(cardId, 0);
            int newCount = newDeckCounts.getOrDefault(cardId, 0);
            if (currentCount > newCount) {
                changed += currentCount - newCount;
            }
        }
        return changed;
    }

    private Map<String, Integer> cardCounts(List<String> cardIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cardId : cardIds) {
            counts.merge(cardId, 1, Integer::sum);
        }
        return counts;
    }

    private List<String> currentDeckCardIds(PlayerState ps, GameState state) {
        List<String> deckCardIds = new ArrayList<>(ps.deck().size());
        for (CardInstId id : ps.deck()) {
            CardInstance card = state.cardInstances().get(id);
            if (card != null) {
                deckCardIds.add(card.defId().value());
            }
        }
        return List.copyOf(deckCardIds);
    }

    private Map<String, Integer> lockedCardCountsInCurrentDeck(List<String> currentDeckCardIds, List<OwnedCard> ownedCards) {
        Map<String, Integer> deckCounts = new LinkedHashMap<>();
        for (String cardId : currentDeckCardIds) {
            deckCounts.merge(cardId, 1, Integer::sum);
        }

        Map<String, Integer> ownedLockedCounts = new LinkedHashMap<>();
        for (OwnedCard owned : ownedCards) {
            if (owned.lockedInDeck()) {
                ownedLockedCounts.merge(owned.cardId(), 1, Integer::sum);
            }
        }

        Map<String, Integer> out = new LinkedHashMap<>();
        for (var e : deckCounts.entrySet()) {
            int lockedCount = ownedLockedCounts.getOrDefault(e.getKey(), 0);
            if (lockedCount > 0) {
                out.put(e.getKey(), Math.min(e.getValue(), lockedCount));
            }
        }
        return out;
    }

    private boolean isLockedInDeck(OwnedCardDto dto) {
        return dto.lockedInDeck() != null && dto.lockedInDeck();
    }

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
            if (!PASSIVE_ID_FORMAT.matcher(id).matches()) {
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

        if (ownedCardsRaw.size() > PlayerState.MAX_OWNED_CARDS) {
            throw new ResponseStatusException(BAD_REQUEST, "ownedCards supports up to 20 items");
        }

        List<OwnedCard> out = new ArrayList<>(ownedCardsRaw.size());
        for (OwnedCardDto dto : ownedCardsRaw) {
            if (dto == null || dto.cardId() == null || dto.cardId().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "ownedCards.cardId is required");
            }
            out.add(new OwnedCard(dto.cardId().trim(), dto.weakened(), isLockedInDeck(dto)));
        }
        return List.copyOf(out);
    }

    private List<String> parsePresetDeckCardIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return defaultPresetDeckCardIds();
        }
        return normalizeDeckCardIds(raw);
    }

    private List<String> normalizeDeckCardIds(List<String> raw) {
        List<String> normalized = new ArrayList<>();
        for (String cardId : raw) {
            if (cardId == null || cardId.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "deckCardIds must not contain blank values");
            }
            normalized.add(cardId.trim());
        }
        return List.copyOf(normalized);
    }

    private String normalizeExCardId(String raw) {
        return (raw == null || raw.isBlank()) ? "EX901" : raw.trim();
    }

    private List<OwnedCard> defaultOwnedCards() {
        List<OwnedCard> owned = new ArrayList<>(20);
        for (int i = 0; i < 5; i++) owned.add(new OwnedCard("C001", false, false));
        for (int i = 0; i < 5; i++) owned.add(new OwnedCard("C002", false, false));
        for (int i = 0; i < 5; i++) owned.add(new OwnedCard("C003", false, false));
        for (int i = 0; i < 5; i++) owned.add(new OwnedCard("C004", false, false));
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
        ZoneOps.createCardInZone(state, ps, defId, Zone.DECK);
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
