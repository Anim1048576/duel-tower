package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.TargetRef;

import java.util.ArrayList;
import java.util.List;

public final class StatusPhases {
    private StatusPhases() {}

    /** 특정 대상(owner)의 '턴 시작'에 상태 효과를 실행 */
    public static void turnStart(GameState state, EngineContext ctx, TargetRef owner, List<GameEvent> out, String source) {
        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        var keys = new ArrayList<>(rt.statusMap(owner).keySet()); // 도중 수정 대비 스냅샷
        for (String key : keys) {
            int stacks = rt.stacks(owner, key);
            if (stacks <= 0) continue;
            if (!ctx.hasStatusEffect(key)) continue;
            ctx.statusEffect(key).onTurnStart(rt, owner, stacks);
        }
    }

    /** 특정 대상(owner)의 '턴 종료'에 상태 효과를 실행 */
    public static void turnEnd(GameState state, EngineContext ctx, TargetRef owner, List<GameEvent> out, String source) {
        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        var keys = new ArrayList<>(rt.statusMap(owner).keySet()); // 도중 수정 대비 스냅샷
        for (String key : keys) {
            int stacks = rt.stacks(owner, key);
            if (stacks <= 0) continue;
            if (!ctx.hasStatusEffect(key)) continue;
            ctx.statusEffect(key).onTurnEnd(rt, owner, stacks);
        }
    }

    /** 전투 종료시 제거 */
    public static void combatEndCleanup(GameState state, EngineContext ctx) {
        for (Ids.PlayerId pid : state.players().keySet()) {
            cleanupOne(TargetRef.ofPlayer(pid), state, ctx);
        }
        for (Ids.EnemyId eid : state.enemies().keySet()) {
            cleanupOne(TargetRef.ofEnemy(eid), state, ctx);
        }
    }

    private static void cleanupOne(TargetRef owner, GameState state, EngineContext ctx) {
        StatusRuntime rt = new StatusRuntime(state, ctx, List.of(), "COMBAT_END"); // out은 안 써도 됨

        var keys = new ArrayList<>(rt.statusMap(owner).keySet());
        for (String key : keys) {
            if (!ctx.hasStatusDef(key)) {
                rt.stacksSet(owner, key, 0);
                continue;
            }
            if (!ctx.statusDef(key).persistsAfterCombat()) {
                rt.stacksSet(owner, key, 0);
            }
        }
    }
}
