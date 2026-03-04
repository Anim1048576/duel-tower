package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.*;
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

        if (ps.pendingDecision() != null) errors.add("pending decision exists");
        return ps;
    }
}
