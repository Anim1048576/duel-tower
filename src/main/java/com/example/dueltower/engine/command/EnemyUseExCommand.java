package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.EnemyExOps;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EnemyUseExCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final EnemyId enemyId;
    private final TargetSelection selection;

    public EnemyUseExCommand(UUID commandId, long expectedVersion, EnemyId enemyId, TargetSelection selection) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.enemyId = enemyId;
        this.selection = (selection == null) ? TargetSelection.empty() : selection;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        EnemyState es = CommandValidation.validateEnemyMainTurn(state, enemyId, errors);
        if (es == null) return errors;
        if (es.exCard() == null) {
            errors.add("ex card not set");
            return errors;
        }

        CombatState cs = state.combat();
        if (cs != null && es.exOnCooldown(cs.round())) {
            errors.add("ex on cooldown");
        }

        EnemyExOps.refreshActivatable(es, cs == null ? 0 : cs.round());
        if (!es.exActivatable()) {
            errors.add("ex is disabled for this combat");
        }

        CardInstId exId = es.exCard();
        CardInstance ci = state.card(exId);
        if (ci == null) {
            errors.add("ex card instance missing: " + exId.value());
            return errors;
        }
        if (!ci.ownerId().value().equals(enemyId.value())) errors.add("ex card is not enemy's");

        CardDefinition def = ctx.def(ci.defId());
        if (def.type() != CardType.EX) errors.add("not an EX card: " + def.id().value());

        StatusOps.validateUseEx(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, errors);

        List<GameEvent> dummyOut = new ArrayList<>();
        int needBase = def.cost();
        int needPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, needBase, dummyOut, "VALIDATE");
        int need = StatusOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, needPassive, dummyOut, "VALIDATE");
        int have = es.ap();
        if (have < need) errors.add("not enough ap (need=" + need + ", have=" + have + ")");

        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, new Ids.PlayerId(enemyId.value()), exId, selection, dummyOut);
        errors.addAll(eff.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        EnemyState es = state.enemy(enemyId);
        if (es == null) throw new IllegalStateException("enemy not found: " + enemyId.value());
        if (state.combat() == null) throw new IllegalStateException("combat not started");
        if (es.exCard() == null) throw new IllegalStateException("ex card not set");

        List<GameEvent> events = new ArrayList<>();

        int round = state.combat().round();
        if (es.exOnCooldown(round)) throw new IllegalStateException("ex on cooldown");

        CardInstId exId = es.exCard();
        CardInstance ci = state.card(exId);
        if (ci == null) throw new IllegalStateException("ex card instance missing: " + exId.value());

        CardDefinition def = ctx.def(ci.defId());

        int costBase = def.cost();
        int costPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, costBase, events, "USE_EX_COST");
        int cost = StatusOps.modifiedCost(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, costPassive, events, "USE_EX_COST");
        if (es.ap() < cost) {
            throw new IllegalStateException("not enough ap during handle (need=" + cost + ", have=" + es.ap() + ")");
        }
        if (cost > 0) es.ap(es.ap() - cost);

        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, new Ids.PlayerId(enemyId.value()), exId, selection, events);
        eff.resolve(ec);

        StatusOps.afterUseEx(state, ctx, TargetRef.ofEnemy(enemyId), ci, def, events, "USE_EX");

        int until = round + 1;
        es.exCooldownUntilRound(until);
        es.usedExThisTurn(true);
        EnemyExOps.refreshActivatable(es, round);

        events.add(new GameEvent.LogAppended(enemyId.value() + " used EX " + def.id().value() + " (cooldown until round " + until + ")"));
        return events;
    }
}
