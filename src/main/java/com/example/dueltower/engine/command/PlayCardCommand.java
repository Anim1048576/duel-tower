package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.LastWordsDecisionOps;
import com.example.dueltower.engine.core.SummonOps;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.LastWordsBatchCollector;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.card.CardEffectOps;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierOps;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayCardCommand implements GameCommand {

    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final CardInstId cardId;
    private final TargetSelection selection;
    private final List<CardInstId> discardIds;
    private final List<CardInstId> selectedIds;

    public PlayCardCommand(UUID commandId, long expectedVersion, PlayerId playerId, CardInstId cardId, TargetSelection selection) {
        this(commandId, expectedVersion, playerId, cardId, selection, List.of(), List.of());
    }

    public PlayCardCommand(
            UUID commandId,
            long expectedVersion,
            PlayerId playerId,
            CardInstId cardId,
            TargetSelection selection,
            List<CardInstId> discardIds
    ) {
        this(commandId, expectedVersion, playerId, cardId, selection, discardIds, List.of());
    }

    public PlayCardCommand(
            UUID commandId,
            long expectedVersion,
            PlayerId playerId,
            CardInstId cardId,
            TargetSelection selection,
            List<CardInstId> discardIds,
            List<CardInstId> selectedIds
    ) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.cardId = cardId;
        this.selection = selection == null ? TargetSelection.empty() : selection;
        this.discardIds = discardIds == null ? List.of() : List.copyOf(discardIds);
        this.selectedIds = selectedIds == null ? List.of() : List.copyOf(selectedIds);
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

        CardModifierOps.validatePlayCard(state, ctx, TargetRef.ofPlayer(playerId), ps, cardId, ci, def, errors);

        // 코스트/AP 체크 (passive -> status 순으로 코스트 변형 적용)
        List<GameEvent> dummyOut = new ArrayList<>();
        int needBase = def.cost();
        int needPassive = PassiveOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, needBase, dummyOut, "VALIDATE");
        int needStatus = StatusOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, needPassive, dummyOut, "VALIDATE");
        int needCard = CardEffectOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), cardId, ci, def, needStatus, dummyOut, "VALIDATE");
        int need = CardModifierOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), cardId, ci, def, needCard, dummyOut, "VALIDATE");
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
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, discardIds, selectedIds, dummyOut);
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
        int costStatus = StatusOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), ci, def, costPassive, events, "PLAY_CARD_COST");
        int costCard = CardEffectOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), cardId, ci, def, costStatus, events, "PLAY_CARD_COST");
        int cost = CardModifierOps.modifiedCost(state, ctx, TargetRef.ofPlayer(playerId), cardId, ci, def, costCard, events, "PLAY_CARD_COST");

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

        LastWordsBatchCollector lastWordsBatchCollector = new LastWordsBatchCollector();
        CardModifierOps.beforeResolvePlayCard(state, ctx, TargetRef.ofPlayer(playerId), ps, cardId, ci, def, events, lastWordsBatchCollector, "PLAY_CARD");

        // 효과 해결
        CardEffect eff = ctx.effect(ci.defId());
        EffectContext ec = new EffectContext(state, ctx, playerId, cardId, selection, discardIds, selectedIds, events, lastWordsBatchCollector);
        eff.resolve(ec);
        LastWordsDecisionOps.openPendingIfPossible(ec, ps, events);

        // 카드 사용 후 훅 순서: passive -> status
        PassiveOps.afterPlayCard(state, ctx, TargetRef.ofPlayer(playerId), ci, def, events, "PLAY_CARD");
        StatusOps.afterPlayCard(state, ctx, TargetRef.ofPlayer(playerId), ci, def, events, "PLAY_CARD");
        CardModifierOps.afterResolvePlayCard(state, ctx, TargetRef.ofPlayer(playerId), ps, cardId, ci, def, events, lastWordsBatchCollector, "PLAY_CARD");

        // 카드 이동 (HAND -> resolveTo)
        if (ps.hand().contains(cardId) && state.card(cardId) != null) {
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, cardId, to, events, MoveReason.PLAY);
            SummonOps.spawnFromCard(state, ctx, ps, cardId);
        }

        // 이번 턴 카드 사용 횟수 트래킹
        ps.incCardsPlayedThisTurn();

        events.add(new GameEvent.CombatLogAppended(
                "combat.playCard",
                "PLAYER",
                ps.playerId().value() + "이 [" + def.name() + "]을 사용했다.",
                ps.playerId().value(),
                ps.playerId().value(),
                targetSummary(),
                targetSummary(),
                def.id().value(),
                def.name(),
                List.of(
                        "대상: " + nullSafeTargetSummary(),
                        "비용: 행동력 " + cost + (debt > 0 ? " (부채 " + debt + ")" : ""),
                        "카드 이동: HAND -> " + to.name(),
                        "카드 ID: " + def.id().value(),
                        "인스턴스: " + cardId.value()
                ),
                playCardLogData(def, cost, debt, to)
        ));
        events.add(new GameEvent.LogAppended(ps.playerId().value() + " plays " + def.id().value()));
        return events;
    }

    private String targetSummary() {
        if (selection == null || selection.targets() == null || selection.targets().isEmpty()) {
            return null;
        }
        return selection.targets().stream()
                .map(PlayCardCommand::targetLabel)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    private String nullSafeTargetSummary() {
        String summary = targetSummary();
        return summary == null || summary.isBlank() ? "없음" : summary;
    }

    private Map<String, Object> playCardLogData(CardDefinition def, int cost, int debt, Zone to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("actorId", playerId.value());
        data.put("cardInstanceId", cardId.value().toString());
        data.put("cardDefId", def.id().value());
        data.put("cardName", def.name());
        data.put("cost", cost);
        data.put("apDebt", debt);
        data.put("targets", selection == null || selection.targets() == null
                ? List.of()
                : selection.targets().stream().map(PlayCardCommand::targetLabel).toList());
        data.put("from", "HAND");
        data.put("to", to.name());
        data.put("reason", "PLAY");
        return data;
    }

    private static String targetLabel(TargetRef target) {
        if (target instanceof TargetRef.Player p) return "PLAYER:" + p.id().value();
        if (target instanceof TargetRef.Enemy e) return "ENEMY:" + e.id().value();
        if (target instanceof TargetRef.Summon s) return "SUMMON:" + s.ownerId().value() + ":" + s.summonId().value();
        return String.valueOf(target);
    }
}
