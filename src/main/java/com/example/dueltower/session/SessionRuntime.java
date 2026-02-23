package com.example.dueltower.session;

import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;

/**
 * 세션 1개당 런타임.
 * - 세션별로 GameEngine 인스턴스를 분리(커맨드 중복처리 Set이 세션 단위가 되게)
 * - apply는 synchronized로 원자 처리
 */
public final class SessionRuntime {

    private final String code;
    private final String gmId;

    private final GameState state;
    private final EngineContext ctx;
    private final GameEngine engine;

    private final Object lock = new Object();

    public SessionRuntime(String code, String gmId, GameState state, EngineContext ctx) {
        this.code = code;
        this.gmId = gmId;
        this.state = state;
        this.ctx = ctx;
        this.engine = new GameEngine();
    }

    public String code() { return code; }
    public String gmId() { return gmId; }

    public GameState state() { return state; }
    public EngineContext ctx() { return ctx; }

    public EngineResult apply(GameCommand cmd) {
        synchronized (lock) {
            return engine.process(state, ctx, cmd);
        }
    }
}