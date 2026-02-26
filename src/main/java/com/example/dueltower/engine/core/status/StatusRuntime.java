package com.example.dueltower.engine.core.status;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.List;
import java.util.Map;

public final class StatusRuntime {
    private final GameState state;
    private final EngineContext ctx;
    private final List<GameEvent> out;
    private final String source;

    public StatusRuntime(GameState state, EngineContext ctx, List<GameEvent> out, String source) {
        this.state = state;
        this.ctx = ctx;
        this.out = out;
        this.source = source;
    }

    public GameState state() { return state; }
    public EngineContext ctx() { return ctx; }
    public List<GameEvent> out() { return out; }
    public String source() { return source; }

    public Map<String, Integer> statusMap(StatusOwnerRef owner) {
        if (owner instanceof StatusOwnerRef.Character c) {
            TargetRef who = c.who();
            if (who instanceof TargetRef.Player p) {
                PlayerState ps = state.player(p.id());
                if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());
                return ps.statusValues();
            }
            if (who instanceof TargetRef.Enemy e) {
                EnemyState es = state.enemy(e.id());
                if (es == null) throw new IllegalStateException("missing enemy: " + e.id().value());
                return es.statusValues();
            }
            throw new IllegalArgumentException("unknown character ref");
        }

        if (owner instanceof StatusOwnerRef.Faction f) {
            CombatState cs = state.combat();
            if (cs == null) throw new IllegalStateException("combat not started");
            return cs.factionStatusValues(f.id());
        }

        if (owner instanceof StatusOwnerRef.Card c) {
            CardInstance ci = state.card(c.id());
            if (ci == null) throw new IllegalStateException("missing card instance: " + c.id().value());
            return ci.counters(); // 카드 상태 저장소로 counters 재사용(최소 구현)
        }

        throw new IllegalArgumentException("unknown owner ref");
    }

    public int stacks(StatusOwnerRef owner, String statusId) {
        return statusMap(owner).getOrDefault(statusId, 0);
    }

    public void stacksSet(StatusOwnerRef owner, String statusId, int value) {
        Map<String,Integer> m = statusMap(owner);
        if (value == 0) m.remove(statusId);
        else m.put(statusId, value);
    }

    public void stacksAdd(StatusOwnerRef owner, String statusId, int delta) {
        if (delta == 0) return;
        stacksSet(owner, statusId, stacks(owner, statusId) + delta);
    }

    public Map<String, Integer> statusMap(TargetRef owner) {
        return statusMap(StatusOwnerRef.of(owner));
    }

    public int stacks(TargetRef owner, String statusId) {
        return stacks(StatusOwnerRef.of(owner), statusId);
    }

    public void stacksSet(TargetRef owner, String statusId, int value) {
        stacksSet(StatusOwnerRef.of(owner), statusId, value);
    }

    public void stacksAdd(TargetRef owner, String statusId, int delta) {
        stacksAdd(StatusOwnerRef.of(owner), statusId, delta);
    }

    public void damage(TargetRef target, int amount) {
        DamageOps.apply(state, ctx, out, source, target, amount);
    }

    public void log(String line) {
        out.add(new GameEvent.LogAppended(line));
    }
}
