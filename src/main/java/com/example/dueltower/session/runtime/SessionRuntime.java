package com.example.dueltower.session.runtime;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 세션 1개당 런타임.
 * - 세션별로 GameEngine 인스턴스를 분리(커맨드 중복처리 Set이 세션 단위가 되게)
 * - apply는 synchronized로 원자 처리
 */
public final class SessionRuntime {

    public record StoredEvent(long cursor, long version, GameEvent event, Instant occurredAt) {}

    private final String code;
    private final String gmId;
    private final String gmToken;
    private final Map<String, String> playerTokensByPlayerId = new ConcurrentHashMap<>();
    private final Map<String, String> playerIdByToken = new ConcurrentHashMap<>();
    private final Map<String, Long> characterIdByPlayerId = new ConcurrentHashMap<>();

    private final GameState state;
    private final EngineContext ctx;
    private final GameEngine engine;
    private final List<StoredEvent> eventHistory = new ArrayList<>();

    private final Object lock = new Object();
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;
    private long nextEventCursor = 1L;

    public SessionRuntime(String code, String gmId, String gmToken, GameState state, EngineContext ctx) {
        this.code = code;
        this.gmId = gmId;
        this.gmToken = gmToken;
        this.state = state;
        this.ctx = ctx;
        this.engine = new GameEngine();
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
    }

    public String code() { return code; }
    public String gmId() { return gmId; }
    public String gmToken() { return gmToken; }

    public String issuePlayerToken(String playerId) {
        return playerTokensByPlayerId.computeIfAbsent(playerId, ignored -> {
            String token = UUID.randomUUID().toString();
            playerIdByToken.put(token, playerId);
            return token;
        });
    }

    public String findPlayerIdByToken(String token) {
        return playerIdByToken.get(token);
    }

    public void bindCharacterId(String playerId, Long characterId) {
        if (playerId == null || characterId == null) {
            return;
        }
        characterIdByPlayerId.put(playerId, characterId);
    }

    public Long findCharacterIdByPlayerId(String playerId) {
        if (playerId == null) {
            return null;
        }
        return characterIdByPlayerId.get(playerId);
    }

    public void removePlayerBindings(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }
        String normalized = playerId.trim();
        String token = playerTokensByPlayerId.remove(normalized);
        if (token != null) {
            playerIdByToken.remove(token);
        }
        characterIdByPlayerId.remove(normalized);
    }

    public void clearPlayerBindings() {
        playerTokensByPlayerId.clear();
        playerIdByToken.clear();
        characterIdByPlayerId.clear();
    }

    public GameState state() { return state; }
    public EngineContext ctx() { return ctx; }
    public Instant createdAt() { return createdAt; }
    public Instant lastAccessedAt() { return lastAccessedAt; }

    public void touchAccess() { this.lastAccessedAt = Instant.now(); }

    public <T> T withLock(Supplier<T> work) {
        synchronized (lock) {
            touchAccess();
            return work.get();
        }
    }

    public EngineResult apply(GameCommand cmd) {
        synchronized (lock) {
            touchAccess();
            EngineResult result = engine.process(state, ctx, cmd);
            if (result.accepted() && !result.events().isEmpty()) {
                long version = state.version();
                Instant occurredAt = Instant.now();
                for (GameEvent event : result.events()) {
                    eventHistory.add(new StoredEvent(nextEventCursor++, version, event, occurredAt));
                }
            }
            return result;
        }
    }

    public List<StoredEvent> eventHistorySnapshot() {
        synchronized (lock) {
            touchAccess();
            return List.copyOf(eventHistory);
        }
    }
}
