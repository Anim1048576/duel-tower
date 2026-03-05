package com.example.dueltower.session.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.session.runtime.SessionRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.*;

@Service
@Slf4j
public class SessionService {

    private final CardService cardService;
    private final StatusService statusService;
    private final KeywordService keywordService;
    private final Duration sessionTtl;
    private final Duration cleanupInterval;

    // code -> runtime (in-memory)
    private final Map<String, SessionRuntime> sessions = new ConcurrentHashMap<>();

    private final SecureRandom rnd = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Set<String> PASSIVE_ID_REGISTRY = Set.of("P001", "P002", "P003");

    public SessionService(CardService cardService,
                          StatusService statusService,
                          KeywordService keywordService,
                          @Value("${duel.session.ttl:30m}") Duration sessionTtl,
                          @Value("${duel.session.cleanup-interval:5m}") Duration cleanupInterval) {
        this.cardService = cardService;
        this.statusService = statusService;
        this.keywordService = keywordService;
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
                    keywordService.effectsMap()
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

    /**
     * 참가: 지금은 프리셋/DB 없이 기본 덱(12) + 기본 EX(1)을 자동 생성.
     * 같은 playerId로 다시 join하면 idempotent.
     */
    public GameState join(String code, String playerIdRaw, List<String> passiveIdsRaw) {
        if (playerIdRaw == null || playerIdRaw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "playerId is required");
        }

        SessionRuntime rt = get(code);
        PlayerId pid = new PlayerId(playerIdRaw.trim());
        List<String> passiveIds = parsePassiveIds(passiveIdsRaw);

        return rt.withLock(() -> {
            GameState state = rt.state();

            if (state.players().containsKey(pid)) {
                log.debug("join is idempotent code={} playerId={}", code, pid.value());
                return state;
            }

            PlayerState ps = new PlayerState(pid);
            ps.passiveIds(passiveIds);
            state.players().put(pid, ps);

            // Default deck: 3x C001 + 3x C002 + 3x C003 + 3x C004
            CardDefId attack = new CardDefId("C001");
            CardDefId recovery = new CardDefId("C002");
            CardDefId guard = new CardDefId("C003");
            CardDefId curse = new CardDefId("C004");

            for (int i = 0; i < 3; i++) addCardToDeck(state, ps, attack);
            for (int i = 0; i < 3; i++) addCardToDeck(state, ps, recovery);
            for (int i = 0; i < 3; i++) addCardToDeck(state, ps, guard);
            for (int i = 0; i < 3; i++) addCardToDeck(state, ps, curse);

            // Default EX: EX901
            addCardToEx(state, ps, new CardDefId("EX901"));

            // Join 시 1회 셔플
            List<CardInstId> list = new ArrayList<>(ps.deck());
            ps.deck().clear();
            Collections.shuffle(list, new Random(state.seed() ^ pid.value().hashCode()));
            for (CardInstId id : list) ps.deck().addLast(id);

            log.debug("player joined code={} playerId={} deckSize={} exId={} passiveIds={}",
                    code,
                    pid.value(),
                    ps.deck().size(),
                    (ps.exCard() == null) ? null : ps.exCard().value(),
                    ps.passiveIds()
            );

            return state;
        });
    }


    private List<String> parsePassiveIds(List<String> passiveIdsRaw) {
        // 기본값 정책: 미지정(null)이면 패시브를 자동 부여하지 않고 빈 목록으로 처리한다.
        if (passiveIdsRaw == null) return List.of();
        if (passiveIdsRaw.size() > PlayerState.MAX_PASSIVES) {
            throw new ResponseStatusException(BAD_REQUEST, "passiveIds supports up to " + PlayerState.MAX_PASSIVES);
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : passiveIdsRaw) {
            if (raw == null || raw.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "passiveIds contains blank id");
            }
            String id = raw.trim();
            if (!PASSIVE_ID_REGISTRY.contains(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "unknown passiveId: " + id);
            }
            if (!normalized.add(id)) {
                throw new ResponseStatusException(BAD_REQUEST, "duplicate passiveId: " + id);
            }
        }

        return List.copyOf(normalized);
    }

    private void addCardToDeck(GameState state, PlayerState ps, CardDefId defId) {
        CardInstId instId = Ids.newCardInstId();
        CardInstance ci = new CardInstance(instId, defId, ps.playerId(), Zone.DECK);
        state.cardInstances().put(instId, ci);
        ps.deck().addLast(instId);
    }

    private void addCardToEx(GameState state, PlayerState ps, CardDefId defId) {
        CardInstId instId = Ids.newCardInstId();
        CardInstance ci = new CardInstance(instId, defId, ps.playerId(), Zone.EX);
        state.cardInstances().put(instId, ci);
        ps.exCard(instId);
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

    @Scheduled(fixedDelayString = "#{@sessionService.cleanupIntervalMillis()}")
    public void cleanupExpiredSessions() {
        evictExpiredSessions();
    }

    public long cleanupIntervalMillis() {
        return cleanupInterval.toMillis();
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
