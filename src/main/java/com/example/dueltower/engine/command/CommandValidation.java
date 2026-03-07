package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.List;

/**
 * Common validation helpers shared by multiple commands.
 */
public final class CommandValidation {
    private CommandValidation() {}

    /**
     * Validates: combat started, player exists, MAIN phase, current actor is playerId, no pending decision.
     *
     * @return PlayerState if found, otherwise null.
     */
    public static PlayerState validateMainTurn(GameState state, PlayerId playerId, List<String> errors) {
        return validateMainTurn(state, playerId, errors, false);
    }

    public static PlayerState validateMainTurn(GameState state, PlayerId playerId, List<String> errors, boolean allowBattleIncapacitated) {
        if (state.combat() == null) errors.add("combat not started");

        PlayerState ps = state.player(playerId);
        if (ps == null) {
            errors.add("player not found");
            return null;
        }

        CombatState cs = state.combat();
        if (cs != null) {
            if (cs.phase() != CombatPhase.MAIN) {
                errors.add("invalid phase: " + cs.phase());
            }
            TargetRef cur = cs.currentTurnActor();
            if (!(cur instanceof TargetRef.Player p) || !p.id().equals(playerId)) {
                errors.add("not your turn");
            }
        }

        if (!allowBattleIncapacitated && (CombatStatuses.isBattleIncapacitated(ps) || ps.hp() <= 0)) {
            errors.add("player is battle incapacitated");
        }

        if (ps.pendingDecision() != null) errors.add("pending decision exists");
        return ps;
    }

    /**
     * Validates: combat started, enemy exists, MAIN phase, current actor is enemyId, no pending decision.
     *
     * @return EnemyState if found, otherwise null.
     */
    public static EnemyState validateEnemyMainTurn(GameState state, EnemyId enemyId, List<String> errors) {
        if (state.combat() == null) errors.add("combat not started");

        EnemyState es = state.enemy(enemyId);
        if (es == null) {
            errors.add("enemy not found");
            return null;
        }

        CombatState cs = state.combat();
        if (cs != null) {
            if (cs.phase() != CombatPhase.MAIN) {
                errors.add("invalid phase: " + cs.phase());
            }
            TargetRef cur = cs.currentTurnActor();
            if (!(cur instanceof TargetRef.Enemy e) || !e.id().equals(enemyId)) {
                errors.add("not enemy turn");
            }
        }

        if (hasAnyPendingDecision(state)) {
            errors.add("pending decision exists");
        }

        return es;
    }

    public static boolean hasAnyPendingDecision(GameState state) {
        return state.players().values().stream().anyMatch(ps -> ps.pendingDecision() != null);
    }
}
