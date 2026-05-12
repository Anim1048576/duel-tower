package com.example.dueltower.engine.command;

import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.CombatEntityOps;
import com.example.dueltower.engine.core.combat.DamageFlags;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ResolveEventHorizonCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String choiceId
) implements GameCommand {
    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (playerId == null) {
            errors.add("playerId is required");
            return errors;
        }
        PlayerState player = state.player(playerId);
        if (player == null) {
            errors.add("player not found");
            return errors;
        }
        if (choiceId == null || choiceId.isBlank()) {
            errors.add("choiceId is required");
            return errors;
        }
        if (!(player.pendingDecision() instanceof PendingDecision.EventHorizonChoice decision)) {
            errors.add("no pending event horizon decision");
            return errors;
        }
        if (!decision.playerId().equals(playerId)) {
            errors.add("event horizon pending owner mismatch");
            return errors;
        }
        if (player.status(Nameless202_EventHorizon.ID) <= 0) {
            errors.add("event horizon status is not active");
        }
        if (!decision.choiceIds().contains(choiceId.trim())) {
            errors.add("invalid event horizon choice");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState player = state.player(playerId);
        if (player == null) throw new IllegalStateException("player not found: " + playerId.value());

        List<GameEvent> events = new ArrayList<>();
        TargetRef self = TargetRef.ofPlayer(playerId);
        String normalizedChoice = choiceId.trim();

        if (Nameless202_EventHorizon.CHOICE_TAKE_DAMAGE.equals(normalizedChoice)) {
            int damage = CombatEntityOps.maxHp(state, self) * 2 / 5;
            DamageOps.apply(
                    state,
                    ctx,
                    events,
                    self,
                    null,
                    "사건의 지평선",
                    Nameless202_EventHorizon.ID,
                    self,
                    damage,
                    DamageFlags.NONE
            );
        } else if (Nameless202_EventHorizon.CHOICE_REMOVE_STATUS.equals(normalizedChoice)) {
            new StatusRuntime(state, ctx, events, "사건의 지평선").stacksSet(self, Nameless202_EventHorizon.ID, 0);
        } else {
            throw new IllegalStateException("invalid event horizon choice: " + normalizedChoice);
        }

        player.pendingDecision(null);
        events.add(new GameEvent.PendingDecisionCleared(playerId.value(), Nameless202_EventHorizon.DECISION_TYPE));
        return events;
    }
}
