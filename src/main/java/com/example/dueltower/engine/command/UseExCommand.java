package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.HandLimitOps;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EX 사용: 코스트 지불 + 효과 실행 + 쿨다운 적용
 * 규칙: 사용 시 "다음 라운드 종료"까지 비활성.
 *
 * EX는 기본적으로 존 이동 없이 비활성화(쿨다운)만 적용한다.
 */
public final class UseExCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final TargetSelection selection;

    public UseExCommand(UUID commandId, long expectedVersion, PlayerId playerId, TargetSelection selection) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.selection = (selection == null) ? TargetSelection.empty() : selection;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (state.combat() == null) errors.add("combat not started");

        PlayerState ps = state.player(playerId);
        if (ps == null) return List.of("player not found");

        CombatState cs = state.combat();
        if (cs != null) {
            TargetRef cur = cs.currentTurnActor();
            if (!(cur instanceof TargetRef.Player p) || !p.id().equals(playerId)) {
                errors.add("not your turn");
            }
        }

        if (ps.pendingDecision() != null) errors.add("pending decision exists");
        if (ps.exCard() == null) {
            errors.add("ex card not set");
            return errors;
        }

        if (cs != null && ps.exOnCooldown(cs.round())) {
            errors.add("ex on cooldown");
        }

        CardInstId exId = ps.exCard();
        CardInstance ci = state.card(exId);
        if (ci == null) {
            errors.add("ex card instance missing: " + exId.value());
            return errors;
        }
        if (!ci.ownerId().equals(playerId)) errors.add("ex card is not yours");

        CardDefinition def = ctx.def(ci.defId());
        if (def.type() != CardType.EX) errors.add("not an EX card: " + def.id().value());

        // 코스트/AP 체크
        int need = def.cost();
        int have = ps.ap();
        if (have < need) errors.add("not enough ap (need=" + need + ", have=" + have + ")");

        // 카드 효과 validate(타겟 등)
        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, exId, selection, List.of());
        errors.addAll(eff.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        if (ps == null) throw new IllegalStateException("player not found: " + playerId.value());
        if (state.combat() == null) throw new IllegalStateException("combat not started");
        if (ps.exCard() == null) throw new IllegalStateException("ex card not set");

        List<GameEvent> events = new ArrayList<>();

        int round = state.combat().round();
        if (ps.exOnCooldown(round)) throw new IllegalStateException("ex on cooldown");

        CardInstId exId = ps.exCard();
        CardInstance ci = state.card(exId);
        if (ci == null) throw new IllegalStateException("ex card instance missing: " + exId.value());

        CardDefinition def = ctx.def(ci.defId());

        // 코스트 지불
        int cost = def.cost();
        if (ps.ap() < cost) {
            throw new IllegalStateException("not enough ap during handle (need=" + cost + ", have=" + ps.ap() + ")");
        }
        if (cost > 0) ps.ap(ps.ap() - cost);

        // 효과 실행
        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, exId, selection, events);
        eff.resolve(ec);

        // EX는 기본적으로 존 이동 없이 '비활성(쿨다운)'만 적용
        int until = round + 1; // 다음 라운드 종료까지
        ps.exCooldownUntilRound(until);
        ps.usedExThisTurn(true);

        // EX 효과로 드로우가 발생했을 수 있으니 hand limit 처리
        HandLimitOps.ensureHandLimitOrPending(state, ctx, ps, events, "hand limit exceeded");

        events.add(new GameEvent.LogAppended(
                ps.playerId().value() + " uses EX " + def.id().value() + " (" + def.name() + ")"
                        + " (cooldown until end of round " + until + ")"
        ));
        return events;
    }
}
