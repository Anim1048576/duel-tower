package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.SummonOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayCardCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final CardInstId cardId;
    private final TargetSelection selection;

    public PlayCardCommand(UUID commandId, long expectedVersion, PlayerId playerId, CardInstId cardId, TargetSelection selection) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.cardId = cardId;
        this.selection = selection == null ? TargetSelection.empty() : selection;
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        PlayerState ps = CommandValidation.validateMainTurn(state, playerId, errors);
        if (ps == null) return errors;

        if (!ps.hand().contains(cardId)) errors.add("card not in hand: " + cardId.value());

        CardInstance ci = state.card(cardId);
        if (ci == null) return List.of("card instance missing: " + cardId.value());
        if (!ci.ownerId().equals(playerId)) errors.add("not your card");

        CardDefinition def = ctx.def(ci.defId());
        Zone toBase = def.resolveTo() == null ? Zone.GRAVE : def.resolveTo();
        Zone to = KeywordOps.overrideMoveDestination(state, ctx, ps, cardId, Zone.HAND, toBase, MoveReason.PLAY);

        // 훅 순서: passive -> status -> keyword
        PassiveOps.validatePlayCard(state, ctx, TargetRef.ofPlayer(playerId), ci, def, errors);

        // 상태에 의한 카드 사용 제한(예: 기절)
        StatusOps.validatePlayCard(state, ctx, TargetRef.ofPlayer(playerId), ci, def, errors);

        // 코스트/AP 체크 (passive -> status 순으로 코스트 변형 적용)
        List<GameEvent> dummyOut = new ArrayList<>();
        int needBase = def.cost();
        int needPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, needBase, dummyOut, "VALIDATE");
        int need = StatusOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, needPassive, dummyOut, "VALIDATE");
        int have = ps.ap();

        // 키워드에 의한 코스트 규칙(집념 등)
        KeywordOps.validateApDebtPayment(state, ctx, ps, cardId, need, have, errors);

        boolean allowDebt = KeywordOps.allowsApDebtPayment(state, ctx, ps, cardId, need, have);
        if (have < need && !allowDebt) {
            errors.add("not enough ap (need=" + need + ", have=" + have + ")");
        }

        // 필드 제한 체크 (resolveTo가 FIELD일 때)
        if (to == Zone.FIELD && ps.field().size() >= ps.fieldLimit()) {
            errors.add("field is full (limit=" + ps.fieldLimit() + ")");
        }

        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, dummyOut);
        errors.addAll(eff.validate(ec));

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        List<GameEvent> events = new ArrayList<>();

        CardInstance ci = state.card(cardId);
        if (ci == null) {
            events.add(new GameEvent.LogAppended("missing card instance: " + cardId.value()));
            return events;
        }

        CardDefinition def = ctx.def(ci.defId());

        Zone toBase = def.resolveTo() == null ? Zone.GRAVE : def.resolveTo();
        Zone to = KeywordOps.overrideMoveDestination(state, ctx, ps, cardId, Zone.HAND, toBase, MoveReason.PLAY);

        // 코스트 지불 (상태에 의한 코스트 증감 포함)
        int costBase = def.cost();
        int costPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, costBase, events, "PLAY_CARD_COST");
        int cost = StatusOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, costPassive, events, "PLAY_CARD_COST");

        int have = ps.ap();
        int debt = 0;

        // 키워드 제약 재검증 (validate에서 걸리지만, 동시성/재검증 안전)
        List<String> kwErrors = new ArrayList<>();
        KeywordOps.validateApDebtPayment(state, ctx, ps, cardId, cost, have, kwErrors);
        if (!kwErrors.isEmpty()) {
            throw new IllegalStateException(String.join("; ", kwErrors));
        }

        if (have < cost) {
            boolean allowDebt = KeywordOps.allowsApDebtPayment(state, ctx, ps, cardId, cost, have);
            if (!allowDebt) {
                throw new IllegalStateException("not enough ap during handle (need=" + cost + ", have=" + have + ")");
            }
            debt = KeywordOps.apDebtAmount(state, ctx, ps, cardId, cost, have);
            ps.ap(0);
        } else {
            if (cost > 0) ps.ap(have - cost);
        }

        // 키워드 후처리(턴당 1장 트래킹, AP debt 기록 등)
        KeywordOps.onAfterPlayCard(state, ctx, ps, cardId, cost, have, debt);

        // 효과 해결
        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, events);
        eff.resolve(ec);

        // 카드 사용 후 훅 순서: passive -> status
        PassiveOps.afterPlayCard(state, ctx, TargetRef.ofPlayer(playerId), ci, def, events, "PLAY_CARD");
        StatusOps.afterPlayCard(state, ctx, TargetRef.ofPlayer(playerId), ci, def, events, "PLAY_CARD");

        // 카드 이동 (HAND -> resolveTo)
        if (ps.hand().contains(cardId) && state.card(cardId) != null) {
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, cardId, to, events, MoveReason.PLAY);
            SummonOps.spawnFromCard(state, ctx, ps, cardId);
        }

        // 이번 턴 카드 사용 횟수 트래킹
        ps.incCardsPlayedThisTurn();

        events.add(new GameEvent.LogAppended(ps.playerId().value() + " plays " + def.id().value()));
        return events;
    }
}