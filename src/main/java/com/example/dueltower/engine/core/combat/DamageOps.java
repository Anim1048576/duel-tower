package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.*;

public final class DamageOps {
    private DamageOps() {}

    public static void apply(GameState state, EngineContext ctx, List<GameEvent> out, String source, TargetRef target, int amount) {
        if (amount <= 0) return;

        StatusRuntime rt = new StatusRuntime(state, ctx, out, source);

        int remaining = amount;

        // 우선순위(priority)가 낮은 상태부터 처리
        Map<String, Integer> statusMap = rt.statusMap(target);
        List<String> keys = new ArrayList<>(statusMap.keySet());
        keys.sort(Comparator.comparingInt(k -> ctx.hasStatusDef(k) ? ctx.statusDef(k).priority() : Integer.MAX_VALUE));

        for (String k : keys) {
            if (remaining <= 0) { remaining = 0; break; }
            if (!ctx.hasStatusEffect(k)) continue;
            remaining = ctx.statusEffect(k).onIncomingDamage(rt, target, remaining);
        }

        if (remaining <= 0) return;

        if (target instanceof TargetRef.Player p) {
            PlayerState ps = state.player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());
            ps.hp(ps.hp() - remaining);
            out.add(new GameEvent.LogAppended(
                    source + " deals " + remaining + " to PLAYER:" + p.id().value() + " (hp=" + ps.hp() + "/" + ps.maxHp() + ")"
            ));
            return;
        }

        if (target instanceof TargetRef.Enemy e) {
            EnemyState es = state.enemy(e.id());
            if (es == null) throw new IllegalStateException("missing enemy: " + e.id().value());
            es.hp(es.hp() - remaining);
            out.add(new GameEvent.LogAppended(
                    source + " deals " + remaining + " to ENEMY:" + e.id().value() + " (hp=" + es.hp() + "/" + es.maxHp() + ")"
            ));
        }
    }
}
