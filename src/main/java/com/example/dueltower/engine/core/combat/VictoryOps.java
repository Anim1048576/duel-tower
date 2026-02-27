package com.example.dueltower.engine.core.combat;

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
        boolean anyPlayerAlive = state.players().values().stream().anyMatch(ps -> ps.hp() > 0);
        boolean anyEnemyAlive = state.enemies().values().stream().anyMatch(es -> es.hp() > 0);

        if (!anyPlayerAlive) return Outcome.PLAYERS_LOSE;
        if (!anyEnemyAlive) return Outcome.PLAYERS_WIN;
        return Outcome.NONE;
    }

    /**
     * Run a check and, if ended, set combat phase to END and clear pending decisions.
     *
     * @return outcome (NONE if not ended)
     */
    public static Outcome postHandleCheck(GameState state, List<GameEvent> out) {
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

        out.add(new GameEvent.LogAppended("combat ends: " + oc));
        return oc;
    }

    private static String decisionType(PendingDecision pd) {
        if (pd instanceof PendingDecision.DiscardToHandLimit) return "DISCARD_TO_HAND_LIMIT";
        if (pd instanceof PendingDecision.SearchPick) return "SEARCH_PICK";
        return "UNKNOWN";
    }
}
