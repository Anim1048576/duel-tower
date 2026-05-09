package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.card.CardEffectOps;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.core.reaction.ReactionOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EnemyPlayCardCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final EnemyId enemyId;
    private final CardInstId cardId;
    private final TargetSelection selection;

    public EnemyPlayCardCommand(UUID commandId, long expectedVersion, EnemyId enemyId, CardInstId cardId, TargetSelection selection) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.enemyId = enemyId;
        this.cardId = cardId;
        this.selection = selection == null ? TargetSelection.empty() : selection;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        EnemyState es = CommandValidation.validateEnemyMainTurn(state, enemyId, errors);
        if (es == null) return errors;

        CardInstance ci = state.card(cardId);
        if (ci == null) return List.of("card instance missing: " + cardId.value());

        // 최소 적응: 적 카드도 ownerId(PlayerId)로 관리되고 enemyId value를 owner 키로 사용한다.
        if (!ci.ownerId().value().equals(enemyId.value())) errors.add("not enemy card");
        if (ci.zone() != Zone.HAND) errors.add("card not in hand: " + cardId.value());

        CardDefinition def = ctx.def(ci.defId());

        PassiveOps.validatePlayCard(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, errors);
        StatusOps.validatePlayCard(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, errors);

        List<GameEvent> dummyOut = new ArrayList<>();
        int needBase = def.cost();
        int needPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, needBase, dummyOut, "VALIDATE");
        int needStatus = StatusOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, needPassive, dummyOut, "VALIDATE");
        int need = CardEffectOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), cardId, ci, def, needStatus, dummyOut, "VALIDATE");
        int have = es.ap();
        if (have < need) {
            errors.add("not enough ap (need=" + need + ", have=" + have + ")");
        }

        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, new Ids.PlayerId(enemyId.value()), cardId, selection, dummyOut);
        errors.addAll(eff.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        EnemyState es = state.enemy(enemyId);
        if (es == null) throw new IllegalStateException("enemy not found: " + enemyId.value());

        CardInstance ci = state.card(cardId);
        if (ci == null) throw new IllegalStateException("card instance missing: " + cardId.value());

        CardDefinition def = ctx.def(ci.defId());
        List<GameEvent> events = new ArrayList<>();
        Map<PlayerId, Integer> hpBeforeByPlayer = playerHpSnapshot(state);

        int costBase = def.cost();
        int costPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, costBase, events, "PLAY_COST");
        int costStatus = StatusOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, costPassive, events, "PLAY_COST");
        int cost = CardEffectOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), cardId, ci, def, costStatus, events, "PLAY_COST");

        if (es.ap() < cost) {
            throw new IllegalStateException("not enough ap during handle (need=" + cost + ", have=" + es.ap() + ")");
        }
        if (cost > 0) {
            es.ap(es.ap() - cost);
        }

        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, new Ids.PlayerId(enemyId.value()), cardId, selection, events);
        eff.resolve(ec);

        Zone to = def.resolveTo() == null ? Zone.GRAVE : def.resolveTo();
        ci.zone(to);

        events.add(new GameEvent.LogAppended(enemyId.value() + " played " + def.id().value()));
        ReactionOps.openAfterEnemyAttackDamagedSelf(
                state,
                ctx,
                events,
                enemyId,
                hpBeforeByPlayer,
                ReactionOps.SOURCE_ACTION_ENEMY_PLAY_CARD
        );
        return events;
    }

    private static Map<PlayerId, Integer> playerHpSnapshot(GameState state) {
        Map<PlayerId, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<PlayerId, PlayerState> entry : state.players().entrySet()) {
            PlayerState ps = entry.getValue();
            if (ps != null) {
                out.put(entry.getKey(), ps.hp());
            }
        }
        return out;
    }
}
