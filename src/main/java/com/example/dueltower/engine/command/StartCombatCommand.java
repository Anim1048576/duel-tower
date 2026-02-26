package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.*;

public final class StartCombatCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final Ids.PlayerId actorId; // GM(또는 시스템) 표시용

    public StartCombatCommand(UUID commandId, long expectedVersion, Ids.PlayerId actorId) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.actorId = actorId;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() != null) errors.add("combat already started");
        if (state.players().isEmpty()) errors.add("no players joined");
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();

        // 1) 턴 오더 생성 (플레이어 + 적, 랜덤 셔플)
        List<TargetRef> order = new ArrayList<>();
        for (Ids.PlayerId pid : state.players().keySet()) order.add(TargetRef.ofPlayer(pid));
        for (Ids.EnemyId eid : state.enemies().keySet()) order.add(TargetRef.ofEnemy(eid));

        Collections.shuffle(order, new Random(state.seed() ^ state.version()));

        CombatState cs = new CombatState();
        cs.turnOrder().clear();
        cs.turnOrder().addAll(order);
        cs.currentTurnIndex(0);
        cs.round(1);

        state.combat(cs);

        // 2) 전투 시작 손패 4장 드로우(플레이어만)
        for (Ids.PlayerId pid : state.players().keySet()) {
            PlayerState ps = state.player(pid);
            if (ps == null) continue;

            ps.swappedThisTurn(false);
            ps.exCooldownUntilRound(0);

            ZoneOps.drawWithRefill(state, ctx, ps, 4, events);

            HandLimitOps.ensureHandLimitOrPending(state, ctx, ps, events, "hand limit exceeded");

            events.add(new GameEvent.LogAppended(ps.playerId().value() + " draws 4 (combat start)"));
        }

        // 3) 로그 + 현재 턴 알림 이벤트
        String orderStr = order.stream().map(CombatState::actorKey)
                .collect(java.util.stream.Collectors.joining(","));

        events.add(new GameEvent.LogAppended(actorId.value() + " starts combat. order=" + orderStr));
        events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));

        return events;
    }

    private static String join(List<Ids.PlayerId> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(order.get(i).value());
        }
        return sb.toString();
    }
}