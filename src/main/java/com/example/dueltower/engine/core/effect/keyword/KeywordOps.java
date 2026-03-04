package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.DamageFlags;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper methods to run keyword hooks for a card instance.
 */
public final class KeywordOps {
    private KeywordOps() {}

    /**
     * Returns the integer value for a keyword on a card definition, or 0 if not present.
     * For flag keywords, convention is value=1.
     */
    public static int keywordValue(GameState state, EngineContext ctx, Ids.CardInstId cardId, String keywordId) {
        if (cardId == null || keywordId == null) return 0;
        CardInstance ci = state.card(cardId);
        if (ci == null) return 0;
        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return 0;
        Integer v = kws.get(keywordId.trim());
        return v == null ? 0 : v;
    }

    public static boolean hasKeyword(GameState state, EngineContext ctx, Ids.CardInstId cardId, String keywordId) {
        return keywordValue(state, ctx, cardId, keywordId) != 0;
    }

    public static boolean blocksDiscard(
            GameState state, EngineContext ctx, PlayerState ps, CardInstId id, DiscardReason reason
    ) {
        if (id == null) return false;
        CardInstance ci = state.card(id);
        if (ci == null) return false;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return false;

        DiscardCtx dc = new DiscardCtx(ps, id, reason);

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;

            if (!ctx.hasKeywordEffect(rt.id())) continue; // unknown keyword: no behavior
            if (ctx.keywordEffect(rt.id()).blocksDiscard(rt, dc)) return true;
        }
        return false;
    }

    public static List<String> validateDiscard(
            GameState state, EngineContext ctx, PlayerState ps, CardInstId id, DiscardReason reason
    ) {
        List<String> errors = new ArrayList<>();
        validateDiscard(state, ctx, ps, id, reason, errors);
        return errors;
    }

    public static void validateDiscard(
            GameState state, EngineContext ctx, PlayerState ps, CardInstId id, DiscardReason reason, List<String> errors
    ) {
        if (id == null) {
            errors.add("discard id is null");
            return;
        }
        CardInstance ci = state.card(id);
        if (ci == null) {
            errors.add("card instance missing: " + id.value());
            return;
        }

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return;

        DiscardCtx dc = new DiscardCtx(ps, id, reason);

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;

            if (!ctx.hasKeywordEffect(rt.id())) continue; // unknown keyword: no behavior
            ctx.keywordEffect(rt.id()).validateDiscard(rt, dc, errors);
        }
    }

    /**
     * Let keywords override the destination zone for a move.
     * This is typically used for "제외" (PLAY/DESTROY: GRAVE -> EXCLUDED), etc.
     */
    public static Zone overrideMoveDestination(
            GameState state, EngineContext ctx, PlayerState ps, CardInstId id,
            Zone from, Zone to, MoveReason reason
    ) {
        if (id == null) return to;
        CardInstance ci = state.card(id);
        if (ci == null) return to;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return to;

        MoveCtx mc = new MoveCtx(ps, id, from, to, reason == null ? MoveReason.OTHER : reason);
        Zone current = to;

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;

            if (!ctx.hasKeywordEffect(rt.id())) continue; // unknown keyword: no behavior
            Zone next = ctx.keywordEffect(rt.id()).overrideMoveDestination(rt, mc, current);
            current = (next == null) ? current : next;
        }
        return current;
    }

    /**
     * Let keywords override whether an EX card can be activated again in this combat.
     * - current: current activatable flag
     * - reason: why we are checking/updating
     */
    public static boolean overrideExActivatable(
            GameState state,
            EngineContext ctx,
            PlayerState ps,
            CardInstId exId,
            boolean current,
            ExActivationReason reason,
            int round
    ) {
        if (exId == null) return current;

        CardInstance ci = state.card(exId);
        if (ci == null) return current;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return current;

        boolean isExCard = (def.type() == CardType.EX);

        ExActivationCtx ac = new ExActivationCtx(
                ps,
                exId,
                isExCard,
                (reason == null) ? ExActivationReason.OTHER : reason,
                round
        );

        boolean cur = current;

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;

            if (!ctx.hasKeywordEffect(rt.id())) continue; // unknown keyword: no behavior
            cur = ctx.keywordEffect(rt.id()).overrideExActivatable(rt, ac, cur);
        }

        return cur;
    }

    /**
     * Whether keywords on the card indicate that TAUNT should be ignored.
     */
    public static boolean ignoresTaunt(
            GameState state,
            EngineContext ctx,
            TargetRef actor,
            Ids.CardInstId cardId,
            TargetRef chosenEnemy
    ) {
        if (cardId == null) return false;
        CardInstance ci = state.card(cardId);
        if (ci == null) return false;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return false;

        EnemyOneTargetCtx tc = new EnemyOneTargetCtx(actor, cardId, chosenEnemy);

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue;

            if (ctx.keywordEffect(rt.id()).ignoresTaunt(rt, tc)) return true;
        }
        return false;
    }

    /**
     * Validation hook for keywords that can modify AP payment rules.
     */
    public static void validateApDebtPayment(
            GameState state,
            EngineContext ctx,
            PlayerState ps,
            Ids.CardInstId cardId,
            int cost,
            int have,
            List<String> errors
    ) {
        if (cardId == null) return;
        CardInstance ci = state.card(cardId);
        if (ci == null) return;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return;

        ApDebtCtx ac = new ApDebtCtx(ps, cardId);

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue;

            ctx.keywordEffect(rt.id()).validateApDebtPayment(rt, ac, cost, have, errors);
        }
    }

    /**
     * Whether keywords allow playing a card even when AP is insufficient.
     */
    public static boolean allowsApDebtPayment(
            GameState state,
            EngineContext ctx,
            PlayerState ps,
            Ids.CardInstId cardId,
            int cost,
            int have
    ) {
        if (cardId == null) return false;
        CardInstance ci = state.card(cardId);
        if (ci == null) return false;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return false;

        ApDebtCtx ac = new ApDebtCtx(ps, cardId);

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue;

            if (ctx.keywordEffect(rt.id()).allowsApDebtPayment(rt, ac, cost, have)) return true;
        }
        return false;
    }

    /**
     * Compute AP debt amount from keywords. If multiple keywords return a debt, we take the maximum.
     */
    public static int apDebtAmount(
            GameState state,
            EngineContext ctx,
            PlayerState ps,
            Ids.CardInstId cardId,
            int cost,
            int have
    ) {
        if (cardId == null) return 0;
        CardInstance ci = state.card(cardId);
        if (ci == null) return 0;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return 0;

        ApDebtCtx ac = new ApDebtCtx(ps, cardId);
        int best = 0;

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue;

            int d = ctx.keywordEffect(rt.id()).apDebtAmount(rt, ac, cost, have);
            if (d > best) best = d;
        }
        return Math.max(0, best);
    }

    /**
     * Compute damage interaction flags from keywords on the card.
     *
     * <p>Examples:
     * <ul>
     *   <li>필중 -> ignore EVASION</li>
     *   <li>관통 -> ignore SHIELD and BARRIER</li>
     * </ul>
     */
    public static DamageFlags damageFlags(
            GameState state,
            EngineContext ctx,
            TargetRef source,
            Ids.CardInstId cardId,
            TargetRef target
    ) {
        if (cardId == null) return DamageFlags.NONE;
        CardInstance ci = state.card(cardId);
        if (ci == null) return DamageFlags.NONE;

        CardDefinition def = ctx.def(ci.defId());
        Map<String, Integer> kws = def.keywords();
        if (kws == null || kws.isEmpty()) return DamageFlags.NONE;

        DamageKeywordCtx dc = new DamageKeywordCtx(source, cardId, target);

        boolean ignoreEvasion = false;
        boolean ignoreShield = false;
        boolean ignoreBarrier = false;

        for (var e : kws.entrySet()) {
            String kid = (e.getKey() == null) ? "" : e.getKey().trim();
            int val = (e.getValue() == null) ? 1 : e.getValue();

            KeywordRuntime rt = new KeywordRuntime(kid, val);
            if (!rt.present()) continue;
            if (!ctx.hasKeywordEffect(rt.id())) continue;

            var eff = ctx.keywordEffect(rt.id());
            if (eff.ignoresEvasion(rt, dc)) ignoreEvasion = true;
            if (eff.ignoresShield(rt, dc)) ignoreShield = true;
            if (eff.ignoresBarrier(rt, dc)) ignoreBarrier = true;
        }

        if (!ignoreEvasion && !ignoreShield && !ignoreBarrier) return DamageFlags.NONE;
        return new DamageFlags(ignoreEvasion, ignoreShield, ignoreBarrier);
    }
}
