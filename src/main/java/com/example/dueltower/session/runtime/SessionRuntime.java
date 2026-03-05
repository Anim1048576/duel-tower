package com.example.dueltower.session.runtime;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * 세션 1개당 런타임.
 * - 세션별로 GameEngine 인스턴스를 분리(커맨드 중복처리 Set이 세션 단위가 되게)
 * - apply는 synchronized로 원자 처리
 */
public final class SessionRuntime {

    private final String code;
    private final String gmId;
    private final String gmToken;

    private final GameState state;
    private final EngineContext ctx;
    private final GameEngine engine;

    private final Object lock = new Object();
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;

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
            return engine.process(state, ctx, cmd);
        }
    }
}
