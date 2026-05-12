package com.example.dueltower.session.service;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.cardmodifier.service.CardModifierService;
import com.example.dueltower.content.enemy.service.EnemyService;
import com.example.dueltower.content.equip.service.EquipService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.config.EncounterTables;
import com.example.dueltower.engine.config.RunConfigs;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.store.SessionRuntimeStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Session lifecycle service.
 *
 * <p>세션 생성/조회/삭제/만료 정리와 runtime 접근 정책을 담당한다.
 * runtime 저장은 {@link SessionRuntimeStore} 뒤로 숨기고, TTL/expire 판단과
 * 공식 lock 진입점은 lifecycle 계층에 둔다.</p>
 */
@Service
@Slf4j
public class SessionLifecycleService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final CardService cardService;
    private final StatusService statusService;
    private final KeywordService keywordService;
    private final PassiveService passiveService;
    private final CardModifierService cardModifierService;
    private final ItemService itemService;
    private final EquipService equipService;
    private final EnemyService enemyService;
    private final GameRules gameRules;
    private final RewardTableConfig rewardTableConfig;
    private final RunConfigs runConfigs;
    private final EncounterTables encounterTables;
    private final SessionRuntimeStore sessionRuntimeStore;
    private final Duration sessionTtl;
    private final Duration cleanupInterval;

    private final SecureRandom rnd = new SecureRandom();

    public SessionLifecycleService(CardService cardService,
                                   StatusService statusService,
                                   KeywordService keywordService,
                                   PassiveService passiveService,
                                   CardModifierService cardModifierService,
                                   ItemService itemService,
                                   EquipService equipService,
                                   EnemyService enemyService,
                                   GameRules gameRules,
                                   RewardTableConfig rewardTableConfig,
                                   RunConfigs runConfigs,
                                   EncounterTables encounterTables,
                                   SessionRuntimeStore sessionRuntimeStore,
                                   @Value("${duel.session.ttl:30m}") Duration sessionTtl,
                                   @Value("${duel.session.cleanup-interval:5m}") Duration cleanupInterval) {
        this.cardService = cardService;
        this.statusService = statusService;
        this.keywordService = keywordService;
        this.passiveService = passiveService;
        this.cardModifierService = cardModifierService;
        this.itemService = itemService;
        this.equipService = equipService;
        this.enemyService = enemyService;
        this.gameRules = gameRules;
        this.rewardTableConfig = rewardTableConfig;
        this.runConfigs = runConfigs;
        this.encounterTables = encounterTables;
        this.sessionRuntimeStore = sessionRuntimeStore;
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
                    cardModifierService.effectsMap(),
                    itemService.defsMap(),
                    itemService.effectsMap(),
                    equipService.defsMap(),
                    gameRules,
                    rewardTableConfig,
                    encounterTables.encounterTableConfig(),
                    runConfigs.runConfig(),
                    enemyService.defsMap()
            );
            GameState state = new GameState(new SessionId(UUID.randomUUID()), rnd.nextLong(), runConfigs.runConfig());
            SessionRuntime rt = new SessionRuntime(code, gmId, generateGmToken(), state, ctx);

            if (sessionRuntimeStore.putIfAbsent(code, rt)) {
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
        SessionRuntime rt = sessionRuntimeStore.get(code);
        if (rt == null) throw new ResponseStatusException(NOT_FOUND, "session not found");
        if (isExpired(rt)) {
            sessionRuntimeStore.remove(code, rt);
            throw new ResponseStatusException(GONE, "session expired");
        }
        rt.touchAccess();
        return rt;
    }

    public <T> T withSession(String code, Function<SessionRuntime, T> reader) {
        return reader.apply(get(code));
    }

    /**
     * Canonical runtime lock entrypoint for session-scoped mutations and
     * read-modify-read flows.
     */
    public <T> T withLockedSession(String code, Function<SessionRuntime, T> reader) {
        SessionRuntime rt = get(code);
        return rt.withLock(() -> reader.apply(rt));
    }

    @Deprecated(forRemoval = false)
    public <T> T withSessionLock(String code, Function<SessionRuntime, T> reader) {
        return withLockedSession(code, reader);
    }

    public void deleteSession(String code) {
        SessionRuntime rt = sessionRuntimeStore.remove(code);
        if (rt == null) {
            throw new ResponseStatusException(NOT_FOUND, "session not found");
        }
    }

    @Scheduled(fixedDelayString = "${duel.session.cleanup-interval:5m}")
    public void cleanupExpiredSessions() {
        evictExpiredSessions();
    }

    private void evictExpiredSessions() {
        Instant now = Instant.now();
        int removed = 0;

        for (var entry : sessionRuntimeStore.entries()) {
            SessionRuntime rt = entry.getValue();
            Instant expirationBoundary = rt.lastAccessedAt().plus(sessionTtl);
            if (expirationBoundary.isAfter(now)) {
                continue;
            }

            if (sessionRuntimeStore.remove(entry.getKey(), rt)) {
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
}
