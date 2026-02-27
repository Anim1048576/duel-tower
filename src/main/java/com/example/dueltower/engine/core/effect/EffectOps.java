package com.example.dueltower.engine.core.effect;

import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.List;

public final class EffectOps {

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

            // 도발(등) 타겟 강제 규칙 검증
            if (one instanceof TargetRef.Enemy) {
                StatusOps.validateEnemyOneTarget(ec.state(), ec.ctx(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), one, errors);
            }
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

        StatusScope scope = ec.ctx().statusDef(key).scope();
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());

        switch (scope) {
            case CHARACTER -> {
                for (TargetRef ref : resolveTargets(t)) {
                    rt.stacksAdd(StatusOwnerRef.of(ref), key, delta);
                }
            }

            case FACTION -> {
                // 타겟들이 속한 진영에 1회만 적용(중복 방지)
                var factions = new java.util.HashSet<CombatState.FactionId>();
                for (TargetRef ref : resolveTargets(t)) {
                    factions.add(CombatState.factionOf(ref));
                }
                for (CombatState.FactionId f : factions) {
                    rt.stacksAdd(StatusOwnerRef.of(f), key, delta);
                }
            }

            case CARD -> {
                // 최소 구현: "지금 효과를 실행 중인 카드"에 부여
                rt.stacksAdd(StatusOwnerRef.of(ec.cardId()), key, delta);
            }
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
            case ENEMY_ONE -> {
                TargetRef chosen = TargetRef.ofEnemy(ec.selection().requireOneEnemy());
                TargetRef resolved = StatusOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), chosen, ec.out(), ec.actor().value());
                yield List.of(resolved);
            }
            case ANY_ONE -> {
                TargetRef chosen = ec.selection().requireOne();
                if (chosen instanceof TargetRef.Enemy) {
                    TargetRef resolved = StatusOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), chosen, ec.out(), ec.actor().value());
                    yield List.of(resolved);
                }
                yield List.of(chosen);
            }
        };
    }

    private void applyDamage(TargetRef ref, int amount) {
        DamageOps.apply(
                ec.state(),
                ec.ctx(),
                ec.out(),
                TargetRef.ofPlayer(ec.actor()),
                ec.actor().value(),
                ref,
                amount
        );
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
