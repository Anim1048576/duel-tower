package com.example.dueltower.engine.core.effect;

import com.example.dueltower.engine.core.combat.DamageFlags;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.content.keyword.kdb.K011_Critical;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
            if (t == Target.ENEMY_ONE && !(one instanceof TargetRef.Enemy) && !(one instanceof TargetRef.Summon)) {
                errors.add("enemy(one enemy/summon) target required");
            }

            // 도발(등) 타겟 강제 규칙 검증
            if (one instanceof TargetRef.Enemy || one instanceof TargetRef.Summon) {
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

    public void damageWithActorAttack(Target t) {
        damage(t, actor().attackPower());
    }

    public void healWithActorHeal(Target t) {
        heal(t, actor().healPower());
    }

    public void damageWithActorAttackPlus(int bonus, Target t) {
        damage(t, actor().attackPower() + bonus);
    }

    public void addStatusWithActorAttack(Target t, String key) {
        addStatus(t, key, actor().attackPower());
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
                TargetRef chosen = ec.selection().requireOneEnemyOrSummon();
                TargetRef resolved = StatusOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), chosen, ec.out(), ec.actor().value());
                yield List.of(resolved);
            }
            case ANY_ONE -> {
                TargetRef chosen = ec.selection().requireOne();
                if (chosen instanceof TargetRef.Enemy || chosen instanceof TargetRef.Summon) {
                    TargetRef resolved = StatusOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), chosen, ec.out(), ec.actor().value());
                    yield List.of(resolved);
                }
                yield List.of(chosen);
            }
        };
    }

    private void applyDamage(TargetRef ref, int amount) {
        int finalAmount = amount;
        if (isCritical(ref, "damage")) {
            finalAmount *= 2;
            ec.out().add(new GameEvent.LogAppended(ec.actor().value() + " critical! damage x2"));
        }

        DamageFlags flags = KeywordOps.damageFlags(
                ec.state(),
                ec.ctx(),
                TargetRef.ofPlayer(ec.actor()),
                ec.cardId(),
                ref
        );
        DamageOps.apply(
                ec.state(),
                ec.ctx(),
                ec.out(),
                TargetRef.ofPlayer(ec.actor()),
                ec.actor().value(),
                ref,
                finalAmount,
                flags
        );
    }

    private void applyHeal(TargetRef ref, int amount) {
        int finalAmount = amount;
        if (isCritical(ref, "heal")) {
            finalAmount *= 2;
            ec.out().add(new GameEvent.LogAppended(ec.actor().value() + " critical! heal x2"));
        }
        HealOps.apply(
                ec.state(),
                ec.ctx(),
                ec.out(),
                TargetRef.ofPlayer(ec.actor()),
                ec.actor().value(),
                ref,
                finalAmount
        );
    }

    private boolean isCritical(TargetRef target, String kind) {
        int crit = KeywordOps.keywordValue(ec.state(), ec.ctx(), ec.cardId(), K011_Critical.ID);
        if (crit <= 0) return false;

        int chance = Math.max(0, Math.min(100, crit * 10));
        if (chance == 0) return false;

        long mix = ec.state().seed();
        mix ^= (ec.state().version() * 0x9E3779B97F4A7C15L);
        mix ^= ((long) ec.out().size() << 32);
        if (ec.cardId() != null) mix ^= ec.cardId().value().hashCode();
        mix ^= target.toString().hashCode();
        mix ^= kind.hashCode();

        Random rnd = new Random(mix);
        int roll = rnd.nextInt(100) + 1;
        return roll <= chance;
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

    private PlayerState actor() {
        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());
        return me;
    }
}
