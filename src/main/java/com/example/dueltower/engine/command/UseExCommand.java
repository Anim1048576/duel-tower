package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EX 사용 선언(효과 처리는 추후). 현재는 쿨다운만 적용.
 * 규칙: 사용 시 "다음 라운드 종료"까지 비활성.
 */
public final class UseExCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;

    public UseExCommand(UUID commandId, long expectedVersion, PlayerId playerId) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() == null) errors.add("combat not started");

        PlayerState ps = state.player(playerId);
        if (ps == null) {
            errors.add("player not found");
            return errors;
        }

        CombatState cs = state.combat();
        if (cs != null && !cs.currentTurnPlayer().equals(playerId)) errors.add("not your turn");
        if (ps.pendingDecision() != null) errors.add("pending decision exists");
        if (ps.exCard() == null) errors.add("ex card not set");

        if (cs != null && ps.exOnCooldown(cs.round())) {
            errors.add("ex on cooldown");
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        int round = state.combat().round();
        int until = round + 1; // 다음 라운드 종료까지
        ps.exCooldownUntilRound(until);

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " uses EX (cooldown until end of round " + until + ")"));
        return events;
    }
}
