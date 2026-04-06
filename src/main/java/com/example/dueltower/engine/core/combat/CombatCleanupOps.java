package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.core.effect.status.StatusPhases;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared combat-end cleanup used by victory resolution and combat restart bootstrap.
 */
public final class CombatCleanupOps {
    private CombatCleanupOps() {}

    public static void cleanupAfterCombatEnd(GameState state, EngineContext ctx) {
        cleanupAfterCombatEnd(state, ctx, true);
    }

    public static void cleanupAfterCombatEnd(GameState state, EngineContext ctx, boolean clearTransientBattleIncapacitation) {
        StatusPhases.combatEndCleanup(state, ctx);

        for (PlayerState ps : state.players().values()) {
            resetPlayerForCombat(ps, state, clearTransientBattleIncapacitation);
        }

        removeEnemyOwnedCardInstances(state);
    }

    private static void resetPlayerForCombat(PlayerState ps, GameState state, boolean clearTransientBattleIncapacitation) {
        LinkedHashSet<Ids.CardInstId> toDeck = new LinkedHashSet<>();
        toDeck.addAll(ps.hand());
        toDeck.addAll(ps.grave());
        toDeck.addAll(ps.field());
        toDeck.addAll(ps.excluded());

        // 전투 중 테스트/효과 처리에서 존 리스트와 CardInstance가 어긋난 경우를 대비해
        // 소유자 기준으로 누락 카드도 회수한다.
        for (Map.Entry<Ids.CardInstId, CardInstance> e : state.cardInstances().entrySet()) {
            CardInstance ci = e.getValue();
            if (ci == null) continue;
            if (!Objects.equals(ci.ownerId(), ps.playerId())) continue;
            if (ci.zone() == Zone.EX) continue;
            toDeck.add(e.getKey());
        }

        ps.hand().clear();
        ps.grave().clear();
        ps.field().clear();
        ps.excluded().clear();

        for (Ids.CardInstId id : toDeck) {
            CardInstance ci = state.card(id);
            if (ci != null) {
                ci.zone(Zone.DECK);
            }
            ps.deck().addLast(id);
        }

        ps.pendingDecision(null);
        ps.swappedThisTurn(false);
        ps.cardsPlayedThisTurn(0);
        ps.usedExThisTurn(false);
        ps.usedTenacityThisTurn(false);
        ps.tenacityDebtThisTurn(0);
        ps.consumablesUsedThisTurn(0);
        ps.consumablesUsedThisCombat(0);
        ps.exCooldownUntilRound(0);
        ps.exActivatable(true);
        if (clearTransientBattleIncapacitation) {
            ps.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 0);
        }

        for (Ids.SummonInstId summonId : new ArrayList<>(ps.activeSummons())) {
            state.summons().remove(summonId);
        }
        ps.activeSummons().clear();
        ps.summonByCard().clear();
    }

    private static void removeEnemyOwnedCardInstances(GameState state) {
        if (state == null || state.enemies().isEmpty()) {
            return;
        }

        LinkedHashSet<String> enemyOwnerKeys = new LinkedHashSet<>();
        for (Ids.EnemyId enemyId : state.enemies().keySet()) {
            if (enemyId != null) {
                enemyOwnerKeys.add(enemyId.value());
            }
        }

        state.cardInstances().entrySet().removeIf(entry -> {
            CardInstance ci = entry.getValue();
            return ci != null
                    && ci.ownerId() != null
                    && enemyOwnerKeys.contains(ci.ownerId().value());
        });
    }
}
