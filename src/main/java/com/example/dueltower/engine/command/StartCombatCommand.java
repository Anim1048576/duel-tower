package com.example.dueltower.engine.command;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.core.combat.TurnFlow;
import com.example.dueltower.engine.core.effect.status.StatusPhases;
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
        CombatState existing = state.combat();
        if (existing != null && existing.phase() != CombatPhase.END) {
            errors.add("combat already started");
        }
        if (state.players().isEmpty()) errors.add("no players joined");
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();

        resetBeforeCombatStart(state, ctx, events);

        // 1) 참가자 목록(플레이어 + 적)
        List<TargetRef> order = new ArrayList<>();
        for (Ids.PlayerId pid : state.players().keySet()) order.add(TargetRef.ofPlayer(pid));
        for (Ids.EnemyId eid : state.enemies().keySet()) order.add(TargetRef.ofEnemy(eid));

        // 2) 이니셔티브 1D100 굴리기
        Random rng = new Random(state.seed() ^ state.version());
        CombatState cs = new CombatState();

        Map<Integer, List<TargetRef>> byRoll = new HashMap<>();
        for (TargetRef ref : order) {
            int roll = DiceUtility.rollDice(1, 100, rng); // 1..100
            String key = CombatState.actorKey(ref);
            cs.initiatives().put(key, roll);
            byRoll.computeIfAbsent(roll, _k -> new ArrayList<>()).add(ref);
        }

        // 3) 플레이어끼리 동률 그룹 기록(협의 필요 표시용)
        for (Map.Entry<Integer, List<TargetRef>> e : byRoll.entrySet()) {
            List<TargetRef> tied = e.getValue();
            if (tied.size() <= 1) continue;

            List<String> tiedPlayers = tied.stream()
                    .filter(t -> t instanceof TargetRef.Player)
                    .map(CombatState::actorKey)
                    .toList();

            if (tiedPlayers.size() >= 2) {
                cs.initiativeTieGroups().add(tiedPlayers);
            }
        }

        // 4) 이니셔티브 기준 정렬
        // - roll 내림차순
        // - 동률이면 플레이어가 적보다 먼저
        // - 플레이어끼리 동률은(협의 전) 현재는 원래 참가 순서를 유지(정렬 안정성 기대)
        order.sort((a, b) -> {
            int ai = cs.initiatives().get(CombatState.actorKey(a));
            int bi = cs.initiatives().get(CombatState.actorKey(b));
            if (ai != bi) return Integer.compare(bi, ai);

            boolean aPlayer = a instanceof TargetRef.Player;
            boolean bPlayer = b instanceof TargetRef.Player;
            if (aPlayer != bPlayer) return aPlayer ? -1 : 1;
            return 0;
        });

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
            ps.cardsPlayedThisTurn(0);
            ps.usedExThisTurn(false);
            ps.usedTenacityThisTurn(false);
            ps.tenacityDebtThisTurn(0);
            ps.ap(ps.maxAp());

            ZoneOps.drawWithRefill(state, ctx, ps, 4, events);

            HandLimitOps.ensureHandLimitOrPending(state, ctx, ps, events, "hand limit exceeded");

            events.add(new GameEvent.LogAppended(ps.playerId().value() + " draws 4 (combat start)"));
        }

        // 2.5) 동률 결정을 먼저 처리하거나(있으면), 즉시 첫 턴을 시작한다.
        if (!cs.initiativeTieGroups().isEmpty()) {
            cs.phase(CombatPhase.INITIATIVE_TIE_DECISION);
            markInitiativeTiePendingDecisions(state, cs, events);
        } else {
            TurnFlow.initializeFirstTurn(state, ctx, events);
        }

        // 3) 로그 + 현재 턴 알림 이벤트
        for (TargetRef ref : order) {
            String key = CombatState.actorKey(ref);
            events.add(new GameEvent.LogAppended("initiative " + key + " = " + cs.initiatives().get(key)));
        }

        if (!cs.initiativeTieGroups().isEmpty()) {
            events.add(new GameEvent.LogAppended("initiative tie among players: " + cs.initiativeTieGroups()));
        }

        String orderStr = order.stream().map(CombatState::actorKey)
                .collect(java.util.stream.Collectors.joining(","));

        events.add(new GameEvent.LogAppended(actorId.value() + " starts combat. order=" + orderStr));
        if (cs.phase() == CombatPhase.MAIN) {
            events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));
        }

        return events;
    }

    private static void markInitiativeTiePendingDecisions(GameState state, CombatState cs, List<GameEvent> events) {
        for (int idx = 0; idx < cs.initiativeTieGroups().size(); idx++) {
            List<String> group = cs.initiativeTieGroups().get(idx);
            for (String actorKey : group) {
                if (!actorKey.startsWith("P:")) continue;
                Ids.PlayerId pid = new Ids.PlayerId(actorKey.substring(2));
                PlayerState ps = state.player(pid);
                if (ps == null) continue;
                ps.pendingDecision(new PendingDecision.InitiativeTieOrder(
                        "resolve initiative tie order",
                        idx,
                        List.copyOf(group)
                ));
                events.add(new GameEvent.PendingDecisionSet(pid.value(), "INITIATIVE_TIE_ORDER", "resolve initiative tie order"));
            }
        }
    }

    private static void resetBeforeCombatStart(GameState state, EngineContext ctx, List<GameEvent> events) {
        if (state.combat() == null) {
            return;
        }

        StatusPhases.combatEndCleanup(state, ctx);

        for (PlayerState ps : state.players().values()) {
            resetPlayerForCombat(ps, state);
        }

        state.combat(null);
        events.add(new GameEvent.LogAppended("combat state reset"));
    }

    private static void resetPlayerForCombat(PlayerState ps, GameState state) {
        List<Ids.CardInstId> toDeck = new ArrayList<>();
        toDeck.addAll(ps.hand());
        toDeck.addAll(ps.grave());
        toDeck.addAll(ps.field());
        toDeck.addAll(ps.excluded());

        ps.hand().clear();
        ps.grave().clear();
        ps.field().clear();
        ps.excluded().clear();

        for (Ids.CardInstId id : toDeck) {
            CardInstance ci = state.card(id);
            if (ci != null) {
                ci.zone(Zone.DECK);
            }
            ps.deck().addLast(id);
        }

        ps.pendingDecision(null);
        ps.swappedThisTurn(false);
        ps.cardsPlayedThisTurn(0);
        ps.usedExThisTurn(false);
        ps.usedTenacityThisTurn(false);
        ps.tenacityDebtThisTurn(0);
        ps.exCooldownUntilRound(0);
        ps.exActivatable(true);
        ps.ap(ps.maxAp());
        ps.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 0);

        for (Ids.SummonInstId summonId : new ArrayList<>(ps.activeSummons())) {
            state.summons().remove(summonId);
        }
        ps.activeSummons().clear();
        ps.summonByCard().clear();
    }
}
