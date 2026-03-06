package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.List;

public final class CombatEntityOps {
    private CombatEntityOps() {}

    public static void adjustHp(GameState state, EngineContext ctx, List<GameEvent> out, TargetRef ref, int delta) {
        if (ref instanceof TargetRef.Player p) {
            PlayerState ps = requirePlayer(state, p.id());
            int before = ps.hp();
            ps.hp(ps.hp() + delta);
            if (state.combat() != null && before > 0 && ps.hp() <= 0 && !CombatStatuses.isBattleIncapacitated(ps)) {
                ps.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 1);
                out.add(new GameEvent.LogAppended(p.id().value() + " becomes [전투 불능] (hp reached 0)"));
            }
            postHpChanged(state, ctx, out, ref);
            return;
        }

        if (ref instanceof TargetRef.Enemy e) {
            EnemyState es = requireEnemy(state, e.id());
            es.hp(es.hp() + delta);
            postHpChanged(state, ctx, out, ref);
            return;
        }

        if (ref instanceof TargetRef.Summon s) {
            SummonState ss = requireSummon(state, s.summonId());
            ss.hp(ss.hp() + delta);
            postHpChanged(state, ctx, out, ref);
            return;
        }

        throw new IllegalArgumentException("unknown target ref: " + ref);
    }

    public static int hp(GameState state, TargetRef ref) {
        if (ref instanceof TargetRef.Player p) return requirePlayer(state, p.id()).hp();
        if (ref instanceof TargetRef.Enemy e) return requireEnemy(state, e.id()).hp();
        if (ref instanceof TargetRef.Summon s) return requireSummon(state, s.summonId()).hp();
        throw new IllegalArgumentException("unknown target ref: " + ref);
    }

    public static int maxHp(GameState state, TargetRef ref) {
        if (ref instanceof TargetRef.Player p) return requirePlayer(state, p.id()).maxHp();
        if (ref instanceof TargetRef.Enemy e) return requireEnemy(state, e.id()).maxHp();
        if (ref instanceof TargetRef.Summon s) return requireSummon(state, s.summonId()).maxHp();
        throw new IllegalArgumentException("unknown target ref: " + ref);
    }

    public static String hpText(GameState state, TargetRef ref) {
        if (ref instanceof TargetRef.Summon s && state.summon(s.summonId()) == null) return "0/0";
        return hp(state, ref) + "/" + maxHp(state, ref);
    }

    public static String targetLabel(TargetRef ref) {
        if (ref instanceof TargetRef.Player p) return "PLAYER:" + p.id().value();
        if (ref instanceof TargetRef.Enemy e) return "ENEMY:" + e.id().value();
        if (ref instanceof TargetRef.Summon s) return "SUMMON:" + s.ownerId().value() + ":" + s.summonId().value();
        throw new IllegalArgumentException("unknown target ref: " + ref);
    }

    public static void postHpChanged(GameState state, EngineContext ctx, List<GameEvent> out, TargetRef ref) {
        if (!(ref instanceof TargetRef.Summon s)) return;
        SummonState summon = state.summon(s.summonId());
        if (summon == null || summon.hp() > 0) return;

        PlayerState owner = state.player(s.ownerId());
        if (owner == null) throw new IllegalStateException("missing summon owner: " + s.ownerId().value());

        CardInstance source = state.card(summon.sourceCardId());
        if (source != null && source.zone() == Zone.FIELD) {
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, owner, summon.sourceCardId(), Zone.GRAVE, out, MoveReason.DESTROY);
        } else {
            owner.activeSummons().remove(s.summonId());
            owner.summonByCard().entrySet().removeIf(e -> e.getValue().equals(s.summonId()));
            state.summons().remove(s.summonId());
        }

        out.add(new GameEvent.LogAppended("summon destroyed: " + targetLabel(ref)));
    }

    private static PlayerState requirePlayer(GameState state, Ids.PlayerId id) {
        PlayerState ps = state.player(id);
        if (ps == null) throw new IllegalStateException("missing player: " + id.value());
        return ps;
    }

    private static EnemyState requireEnemy(GameState state, Ids.EnemyId id) {
        EnemyState es = state.enemy(id);
        if (es == null) throw new IllegalStateException("missing enemy: " + id.value());
        return es;
    }

    private static SummonState requireSummon(GameState state, Ids.SummonInstId id) {
        SummonState ss = state.summon(id);
        if (ss == null) throw new IllegalStateException("missing summon: " + id.value());
        return ss;
    }
}
