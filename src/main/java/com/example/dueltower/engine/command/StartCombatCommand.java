package com.example.dueltower.engine.command;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.engine.config.EncounterTableConfig;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.combat.CombatCleanupOps;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.core.combat.TurnFlow;
import com.example.dueltower.engine.core.enemy.EnemyStateFactory;
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
        ensureRunEncounterExists(state, ctx, events);
        clearTransientBattleIncapacitation(state);
        resetConsumableUsageCounters(state);

        // 1) 참가자 목록(플레이어 + 적)
        List<TargetRef> order = new ArrayList<>();
        for (Ids.PlayerId pid : state.players().keySet()) {
            PlayerState ps = state.player(pid);
            if (CombatStatuses.isPersistentlyBattleIncapacitated(ps)) continue;
            order.add(TargetRef.ofPlayer(pid));
        }
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
        int firstPlayerIndex = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) instanceof TargetRef.Player) {
                firstPlayerIndex = i;
                break;
            }
        }
        cs.currentTurnIndex(Math.max(firstPlayerIndex, 0));
        cs.round(1);

        state.combat(cs);

        // 2) 전투 시작 손패 규칙 장수 드로우(플레이어만)
        for (Ids.PlayerId pid : state.players().keySet()) {
            PlayerState ps = state.player(pid);
            if (ps == null || CombatStatuses.isPersistentlyBattleIncapacitated(ps)) continue;

            ps.swappedThisTurn(false);
            ps.cardsPlayedThisTurn(0);
            ps.usedExThisTurn(false);
            ps.usedTenacityThisTurn(false);
            ps.tenacityDebtThisTurn(0);

            int openingDrawCount = ctx.gameRules().combatStartDrawCount();
            drawOpeningHand(state, ps, openingDrawCount, events);

            HandLimitOps.ensureHandLimitOrPending(state, ctx, ps, events, "hand limit exceeded");

            events.add(new GameEvent.CombatLogAppended(
                    "combat.draw",
                    "PLAYER",
                    ps.playerId().value() + "이 카드 " + openingDrawCount + "장을 드로우했다.",
                    ps.playerId().value(),
                    ps.playerId().value(),
                    null,
                    null,
                    null,
                    null,
                    List.of("사유: 전투 시작"),
                    Map.of(
                            "actorId", ps.playerId().value(),
                            "count", openingDrawCount,
                            "reason", "COMBAT_START"
                    )
            ));
            events.add(new GameEvent.LogAppended(ps.playerId().value() + " draws " + openingDrawCount + " (combat start)"));
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
        events.add(new GameEvent.CombatLogAppended(
                "combat.initiative",
                "PLAYER",
                "행동 순서 판정: " + initiativeSummary(cs),
                actorId.value(),
                actorId.value(),
                null,
                null,
                null,
                null,
                List.of("판정 결과: " + initiativeSummary(cs)),
                initiativeLogData(cs)
        ));

        if (!cs.initiativeTieGroups().isEmpty()) {
            events.add(new GameEvent.LogAppended("initiative tie among players: " + cs.initiativeTieGroups()));
        }

        String orderStr = order.stream().map(CombatState::actorKey)
                .collect(java.util.stream.Collectors.joining(","));

        events.add(new GameEvent.LogAppended(actorId.value() + " starts combat. order=" + orderStr));
        events.add(new GameEvent.CombatLogAppended(
                "combat.start",
                "PLAYER",
                "전투가 시작되었다.",
                actorId.value(),
                actorId.value(),
                null,
                null,
                null,
                null,
                List.of("행동 순서: " + order.stream().map(StartCombatCommand::actorDisplay).collect(java.util.stream.Collectors.joining(" -> "))),
                combatStartLogData(state, order)
        ));
        if (cs.phase() == CombatPhase.MAIN) {
            events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));
        }

        return events;
    }

    private static void ensureRunEncounterExists(GameState state, EngineContext ctx, List<GameEvent> events) {
        if (!state.enemies().isEmpty()) {
            return;
        }

        EncounterTableConfig.EncounterTemplate encounter = ctx.encounterTable().selectEncounter(state.runState());
        int floorDelta = ctx.encounterTable().resolveFloorDelta(state.runState(), encounter);
        List<EnemyState> enemies = new ArrayList<>();
        Set<Ids.EnemyId> usedEnemyIds = new LinkedHashSet<>();
        for (EncounterTableConfig.EnemyTemplate template : encounter.enemies()) {
            EnemyDefinition definition = ctx.enemyDef(template.enemyDefId());
            EnemyState enemy = EnemyStateFactory.create(template, definition, floorDelta);
            if (!usedEnemyIds.add(enemy.enemyId())) {
                throw new IllegalStateException("duplicate enemy instance id in encounter: " + enemy.enemyId().value());
            }
            enemies.add(enemy);
        }
        if (enemies.isEmpty()) {
            throw new IllegalStateException("encounter must contain at least one enemy: " + encounter.encounterId());
        }

        events.add(new GameEvent.LogAppended("런 인카운터가 선택되었다: " + encounter.encounterId()));
        for (EnemyState enemy : enemies) {
            state.enemies().put(enemy.enemyId(), enemy);
            events.add(new GameEvent.LogAppended("encounter enemy placed: " + enemy.enemyId().value() + " (" + enemy.enemyDefId() + ")"));
            events.add(new GameEvent.LogAppended("런 인카운터 적이 배치되었다: " + enemy.enemyId().value()));
        }
    }

    /**
     * 전투 시작 오프닝 드로우:
     * - 덱 top 순서를 그대로 유지해 드로우한다.
     * - 덱이 비면 grave를 리필+셔플한다.
     * - deck+grave가 모두 비어도 [전투 불능]은 부여하지 않는다.
     */
    private static void drawOpeningHand(GameState state, PlayerState ps, int count, List<GameEvent> events) {
        if (count <= 0) return;

        Random rnd = new Random(state.seed() ^ state.version() ^ ps.playerId().value().hashCode());
        for (int i = 0; i < count; i++) {
            if (ps.deck().isEmpty()) {
                ZoneOps.refillDeckFromGrave(state, ps, events);
                ZoneOps.shuffleDeck(state, ps, events, rnd);
            }
            if (ps.deck().isEmpty()) {
                return;
            }

            Ids.CardInstId top = ps.deck().removeFirst();
            ps.hand().add(top);
            state.card(top).zone(Zone.HAND);
        }
    }

    private static void clearTransientBattleIncapacitation(GameState state) {
        for (PlayerState ps : state.players().values()) {
            if (ps != null) {
                ps.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 0);
            }
        }
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

    private static void resetConsumableUsageCounters(GameState state) {
        for (PlayerState ps : state.players().values()) {
            if (ps == null) continue;
            ps.consumablesUsedThisTurn(0);
            ps.consumablesUsedThisCombat(0);
        }
    }

    private static void resetBeforeCombatStart(GameState state, EngineContext ctx, List<GameEvent> events) {
        if (state.combat() == null) {
            return;
        }

        CombatCleanupOps.cleanupAfterCombatEnd(state, ctx);

        state.combat(null);
        state.enemies().clear();
        events.add(new GameEvent.LogAppended("combat state reset"));
    }

    private static String initiativeSummary(CombatState cs) {
        return cs.initiatives().entrySet().stream()
                .map(entry -> actorDisplay(entry.getKey()) + " " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static Map<String, Object> initiativeLogData(CombatState cs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rolls", Map.copyOf(cs.initiatives()));
        data.put("summaries", cs.initiatives().entrySet().stream()
                .map(entry -> actorDisplay(entry.getKey()) + " " + entry.getValue())
                .toList());
        if (!cs.initiativeTieGroups().isEmpty()) {
            data.put("tieGroups", List.copyOf(cs.initiativeTieGroups()));
        }
        return Map.copyOf(data);
    }

    private static Map<String, Object> combatStartLogData(GameState state, List<TargetRef> order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", order.stream().map(CombatState::actorKey).toList());
        data.put("enemies", state.enemies().keySet().stream().map(Ids.EnemyId::value).toList());
        if (state.runState() != null && state.runState().currentNode() != null) {
            data.put("encounterId", state.runState().currentNode().id());
            data.put("encounterName", state.runState().currentNode().name());
        }
        return Map.copyOf(data);
    }

    private static String actorDisplay(TargetRef ref) {
        return actorDisplay(CombatState.actorKey(ref));
    }

    private static String actorDisplay(String actorKey) {
        if (actorKey == null || actorKey.isBlank()) {
            return "알 수 없음";
        }
        if (actorKey.startsWith("P:") || actorKey.startsWith("E:")) {
            return actorKey.substring(2);
        }
        return actorKey;
    }
}
