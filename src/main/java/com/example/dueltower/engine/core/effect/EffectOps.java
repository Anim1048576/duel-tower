package com.example.dueltower.engine.core.effect;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.TargetRef;

import java.util.ArrayList;
import java.util.List;

public final class EffectOps {
    public static final String SHIELD = "SHIELD";

    private final EffectContext ec;

    public EffectOps(EffectContext ec) {
        this.ec = ec;
    }

    public List<String> validateTarget(Target t) {
        List<String> errors = new ArrayList<>();
        if (t == Target.NONE) return errors;

        if (t == Target.SELF) return errors;

        if (t == Target.ALLY_ALL || t == Target.ALLY_SIDE) return errors;
        if (t == Target.ENEMY_ALL || t == Target.ENEMY_SIDE) return errors;

        if (t == Target.ANY_ONE || t == Target.ALLY_ONE || t == Target.ENEMY_ONE) {
            if (ec.selection() == null || ec.selection().targets() == null || ec.selection().targets().size() != 1) {
                errors.add("exactly one target is required");
                return errors;
            }
            TargetRef one = ec.selection().targets().get(0);
            if (t == Target.ALLY_ONE && !(one instanceof TargetRef.Player)) errors.add("ally(one player) target required");
            if (t == Target.ENEMY_ONE && !(one instanceof TargetRef.Enemy)) errors.add("enemy(one enemy) target required");
        }
        return errors;
    }

    public void damage(Target t, int amount) {
        if (amount <= 0) return;
        for (TargetRef ref : resolveTargets(t)) {
            applyDamage(ref, amount);
        }
    }

    public void heal(Target t, int amount) {
        if (amount <= 0) return;
        for (TargetRef ref : resolveTargets(t)) {
            applyHeal(ref, amount);
        }
    }

    public void addStatus(Target t, String key, int delta) {
        if (delta == 0) return;
        for (TargetRef ref : resolveTargets(t)) {
            applyStatus(ref, key, delta);
        }
    }

    private List<TargetRef> resolveTargets(Target t) {
        return switch (t) {
            case NONE -> List.of();

            case SELF -> List.of(TargetRef.ofPlayer(ec.actor()));

            case ALLY_ALL, ALLY_SIDE ->
                    ec.state().players().keySet().stream().map(TargetRef::ofPlayer).toList();

            case ENEMY_ALL, ENEMY_SIDE ->
                    ec.state().enemies().keySet().stream().map(TargetRef::ofEnemy).toList();

            case ALLY_ONE -> List.of(TargetRef.ofPlayer(ec.selection().requireOnePlayer()));
            case ENEMY_ONE -> List.of(TargetRef.ofEnemy(ec.selection().requireOneEnemy()));
            case ANY_ONE -> List.of(ec.selection().requireOne());
        };
    }

    private void applyDamage(TargetRef ref, int amount) {
        if (ref instanceof TargetRef.Player p) {
            PlayerState ps = ec.state().player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());

            int remaining = amount;

            int shield = ps.status(SHIELD);
            if (shield > 0) {
                int absorbed = Math.min(shield, remaining);
                ps.statusSet(SHIELD, shield - absorbed);
                remaining -= absorbed;
            }

            if (remaining > 0) ps.hp(ps.hp() - remaining);

            ec.out().add(new GameEvent.LogAppended(
                    ec.actor().value() + " deals " + amount + " to PLAYER:" + p.id().value() + " (hp=" + ps.hp() + "/" + ps.maxHp() + ")"
            ));
            return;
        }

        if (ref instanceof TargetRef.Enemy e) {
            EnemyState es = ec.state().enemy(e.id());
            if (es == null) throw new IllegalStateException("missing enemy: " + e.id().value());

            int remaining = amount;

            int shield = es.status(SHIELD);
            if (shield > 0) {
                int absorbed = Math.min(shield, remaining);
                es.statusSet(SHIELD, shield - absorbed);
                remaining -= absorbed;
            }

            if (remaining > 0) es.hp(es.hp() - remaining);

            ec.out().add(new GameEvent.LogAppended(
                    ec.actor().value() + " deals " + amount + " to ENEMY:" + e.id().value() + " (hp=" + es.hp() + "/" + es.maxHp() + ")"
            ));
        }
    }

    private void applyHeal(TargetRef ref, int amount) {
        if (ref instanceof TargetRef.Player p) {
            PlayerState ps = ec.state().player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());

            ps.hp(ps.hp() + amount);

            ec.out().add(new GameEvent.LogAppended(
                    ec.actor().value() + " heals " + amount + " to PLAYER:" + p.id().value() + " (hp=" + ps.hp() + "/" + ps.maxHp() + ")"
            ));
            return;
        }

        if (ref instanceof TargetRef.Enemy e) {
            EnemyState es = ec.state().enemy(e.id());
            if (es == null) throw new IllegalStateException("missing enemy: " + e.id().value());

            es.hp(es.hp() + amount);

            ec.out().add(new GameEvent.LogAppended(
                    ec.actor().value() + " heals " + amount + " to ENEMY:" + e.id().value() + " (hp=" + es.hp() + "/" + es.maxHp() + ")"
            ));
        }
    }

    private void applyStatus(TargetRef ref, String key, int delta) {
        if (ref instanceof TargetRef.Player p) {
            PlayerState ps = ec.state().player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());
            ps.statusAdd(key, delta);
            return;
        }
        if (ref instanceof TargetRef.Enemy e) {
            EnemyState es = ec.state().enemy(e.id());
            if (es == null) throw new IllegalStateException("missing enemy: " + e.id().value());
            es.statusAdd(key, delta);
        }
    }
}