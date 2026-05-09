package com.example.dueltower.engine.core.reaction;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.core.effect.card.ReactionEffectContext;
import com.example.dueltower.engine.core.effect.card.ReactiveCardEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.ReactionTrigger;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReactionOps {
    public static final String DECISION_TYPE = "REACTION_CARD";
    public static final String SOURCE_ACTION_ENEMY_PLAY_CARD = "ENEMY_PLAY_CARD";

    private ReactionOps() {}

    public static void openAfterEnemyAttackDamagedSelf(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            EnemyId sourceEnemyId,
            Map<PlayerId, Integer> hpBeforeByPlayer,
            String sourceAction
    ) {
        if (state == null || ctx == null || out == null || sourceEnemyId == null || hpBeforeByPlayer == null) return;
        if (ctx.resolvingReaction()) return;
        if (state.enemy(sourceEnemyId) == null) return;

        for (Map.Entry<PlayerId, Integer> entry : hpBeforeByPlayer.entrySet()) {
            PlayerId playerId = entry.getKey();
            PlayerState ps = state.player(playerId);
            if (ps == null) continue;

            int before = entry.getValue() == null ? ps.hp() : entry.getValue();
            int damageTaken = Math.max(0, before - ps.hp());
            if (damageTaken <= 0) continue;
            if (!canOpenReactionFor(ps)) continue;

            PendingDecision.ReactionContext reactionContext = new PendingDecision.ReactionContext(
                    UUID.randomUUID(),
                    playerId,
                    ReactionTrigger.AFTER_ENEMY_ATTACK_DAMAGED_SELF,
                    TargetRef.ofEnemy(sourceEnemyId),
                    TargetRef.ofPlayer(playerId),
                    damageTaken,
                    sourceAction == null || sourceAction.isBlank() ? SOURCE_ACTION_ENEMY_PLAY_CARD : sourceAction
            );

            List<CardInstId> candidates = candidateIds(state, ctx, ps, reactionContext);
            if (candidates.isEmpty()) continue;

            if (hasAnyPendingDecision(state)) {
                out.add(new GameEvent.LogAppended(
                        "reaction pending skipped for " + playerId.value() + " because another pending decision exists"
                ));
                return;
            }

            ps.pendingDecision(new PendingDecision.ReactionCard(
                    ReactionTrigger.AFTER_ENEMY_ATTACK_DAMAGED_SELF.name(),
                    candidates,
                    true,
                    reactionContext
            ));
            out.add(new GameEvent.PendingDecisionSet(
                    ps.playerId().value(),
                    DECISION_TYPE,
                    ReactionTrigger.AFTER_ENEMY_ATTACK_DAMAGED_SELF.name()
            ));
            return;
        }
    }

    private static boolean canOpenReactionFor(PlayerState ps) {
        return ps.hp() > 0 && !CombatStatuses.isBattleIncapacitated(ps);
    }

    private static boolean hasAnyPendingDecision(GameState state) {
        return state.players().values().stream().anyMatch(ps -> ps.pendingDecision() != null);
    }

    private static List<CardInstId> candidateIds(
            GameState state,
            EngineContext ctx,
            PlayerState ps,
            PendingDecision.ReactionContext reactionContext
    ) {
        List<CardInstId> out = new ArrayList<>();
        for (CardInstId cardId : ps.hand()) {
            CardInstance ci = state.card(cardId);
            if (ci == null || ci.zone() != Zone.HAND || !ps.playerId().equals(ci.ownerId())) continue;
            try {
                if (ctx.effect(ci.defId()) instanceof ReactiveCardEffect reactive
                        && reactive.canReact(new ReactionEffectContext(state, ctx, ps.playerId(), cardId, reactionContext, List.of()))) {
                    out.add(cardId);
                }
            } catch (IllegalArgumentException ignored) {
                // A missing effect cannot be used as a reaction candidate.
            }
        }
        return List.copyOf(out);
    }
}
