package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.config.RunConfig;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.List;

/**
 * Post-command victory/defeat checks.
 *
 * Goal: after ANY command resolves (card, EX, status ticks, end turn, etc.),
 * ensure combat ends immediately if either side has no living units.
 */
public final class VictoryOps {
    private VictoryOps() {}

    public enum Outcome {
        NONE,
        PLAYERS_WIN,
        PLAYERS_LOSE
    }

    public static Outcome check(GameState state) {
        // 승패 주체는 플레이어/적 본체만이며, 소환체는 포함하지 않는다.
        boolean anyPlayerPrincipalAlive = state.players().values().stream()
                .anyMatch(ps -> ps.hp() > 0 && !CombatStatuses.isBattleIncapacitated(ps));
        boolean anyEnemyPrincipalAlive = state.enemies().values().stream().anyMatch(es -> es.hp() > 0);

        if (!anyPlayerPrincipalAlive) return Outcome.PLAYERS_LOSE;
        if (!anyEnemyPrincipalAlive) return Outcome.PLAYERS_WIN;
        return Outcome.NONE;
    }

    /**
     * Run a check and, if ended, set combat phase to END and clear pending decisions.
     *
     * @return outcome (NONE if not ended)
     */
    public static Outcome postHandleCheck(GameState state, EngineContext ctx, List<GameEvent> out) {
        CombatState cs = state.combat();
        if (cs == null) return Outcome.NONE;
        if (cs.phase() == CombatPhase.END) return Outcome.NONE;

        CombatPhase prev = cs.phase();
        cs.phase(CombatPhase.CHECK_VICTORY);

        Outcome oc = check(state);
        if (oc == Outcome.NONE) {
            cs.phase(prev);
            return Outcome.NONE;
        }

        // End combat
        cs.phase(CombatPhase.END);

        // Clear all pending decisions (they are irrelevant once combat is over)
        for (PlayerState ps : state.players().values()) {
            PendingDecision pd = ps.pendingDecision();
            if (pd != null) {
                out.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), decisionType(pd)));
                ps.pendingDecision(null);
            }
        }

        // Run shared combat-end cleanup before combat context is released.
        CombatCleanupOps.cleanupAfterCombatEnd(state, ctx, false);

        // Record post-combat result and release combat context.
        RunConfig.RunNodeDefinition nodeDefinition = state.runState().currentNodeDefinition();
        boolean bossCombat = nodeDefinition != null && nodeDefinition.nodeType() == RunConfig.NodeType.BOSS;
        if (oc == Outcome.PLAYERS_WIN) {
            boolean floorClearedByBoss = bossCombat && state.runState().markCurrentFloorClearedByBoss();
            state.runState().resolveCurrentNode(
                    "combat",
                    bossCombat ? "보스 전투 결과" : "전투 결과",
                    bossCombat ? "보스 전투 승리" : "전투 승리",
                    bossCombat
                            ? "보스를 제압하고 300G와 상자 1개를 확보했다. 현재 층이 안전 구획으로 전환되어 다음 층으로 진입할 수 있다."
                            : "적을 제압하고 180G와 상자 1개를 확보했다.",
                    bossCombat ? 300 : 180,
                    0,
                    1
            );
            if (floorClearedByBoss) {
                out.add(new GameEvent.LogAppended("보스 처치: 현재 층이 안전 구획으로 전환되었다."));
            }
        } else {
            state.runState().resolveCurrentNode(
                    "combat",
                    bossCombat ? "보스 전투 결과" : "전투 결과",
                    bossCombat ? "보스 전투 패배" : "전투 패배",
                    bossCombat
                            ? "보스에게 밀려 추가 보상 없이 후퇴했다."
                            : "전열이 무너져 추가 보상 없이 후퇴했다.",
                    0,
                    0,
                    0
            );
        }
        state.combat(null);
        state.enemies().clear();

        if (bossCombat) {
            out.add(new GameEvent.LogAppended("boss combat ends: " + oc));
        } else {
            out.add(new GameEvent.LogAppended("combat ends: " + oc));
        }
        return oc;
    }

    private static String decisionType(PendingDecision pd) {
        if (pd instanceof PendingDecision.DiscardToHandLimit) return "DISCARD_TO_HAND_LIMIT";
        if (pd instanceof PendingDecision.SearchPick) return "SEARCH_PICK";
        if (pd instanceof PendingDecision.InitiativeTieOrder) return "INITIATIVE_TIE_ORDER";
        if (pd instanceof PendingDecision.JudgementChoice) return "JUDGEMENT";
        if (pd instanceof PendingDecision.LastWordsChoice) return "LAST_WORDS";
        if (pd instanceof PendingDecision.ReactionCard) return "REACTION_CARD";
        if (pd instanceof PendingDecision.EventHorizonChoice) return "EVENT_HORIZON";
        return "UNKNOWN";
    }
}
