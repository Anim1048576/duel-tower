package com.example.dueltower.engine.core.effect;

import com.example.dueltower.common.util.Rational;
import com.example.dueltower.engine.core.combat.DamageFlags;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.EquipmentCombatOps;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierOps;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
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
                StatusOps.validateEnemyOneTarget(ec.state(), ec.ctx(), actorRef(), ec.cardId(), one, errors);
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

    public void damageSelected(List<TargetRef> targets, int amount, int hits) {
        if (amount <= 0 || hits <= 0 || targets == null || targets.isEmpty()) return;

        TargetRef src = actorRef();
        for (TargetRef chosen : targets) {
            TargetRef resolved = StatusOps.resolveEnemyOneTarget(
                    ec.state(),
                    ec.ctx(),
                    src,
                    ec.cardId(),
                    chosen,
                    ec.out(),
                    ec.actor().value()
            );
            CardInstance ci = ec.state().card(ec.cardId());
            if (ci != null) {
                resolved = CardModifierOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), src, ec.cardId(), ci, ec.ctx().def(ci.defId()), resolved,
                        enemyCandidatesFor(src), ec.out(), ec.actor().value());
            }
            DamageFlags flags = KeywordOps.damageFlags(ec.state(), ec.ctx(), src, ec.cardId(), resolved);
            for (int i = 0; i < hits; i++) {
                DamageOps.apply(ec.state(), ec.ctx(), ec.out(), src, ec.cardId(), ec.actor().value(), resolved, amount, flags);
            }
        }
    }

    public void heal(Target t, int amount) {
        if (amount <= 0) return;
        for (TargetRef ref : resolveTargets(t)) {
            applyHeal(ref, amount);
        }
    }

    public void damageWithActorAttack(Target t) {
        damage(t, actorAttackPower());
    }

    public void healWithActorHeal(Target t) {
        heal(t, actor().healPower());
    }

    public void damageWithActorAttackPlus(int bonus, Target t) {
        damage(t, actorAttackPower() + bonus);
    }

    public void addStatusWithActorAttack(Target t, String key) {
        addStatus(t, key, actorAttackPower());
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

            case SELF -> List.of(actorRef());

            case ALLY_ALL, ALLY_SIDE ->
                    ec.state().players().keySet().stream().map(TargetRef::ofPlayer).toList();

            case ENEMY_ALL, ENEMY_SIDE ->
                    ec.state().enemies().keySet().stream().map(TargetRef::ofEnemy).toList();

            case ALLY_ONE -> List.of(TargetRef.ofPlayer(ec.selection().requireOnePlayer()));
            case ENEMY_ONE -> {
                TargetRef chosen = ec.selection().requireOneEnemyOrSummon();
                TargetRef resolved = StatusOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), actorRef(), ec.cardId(), chosen, ec.out(), ec.actor().value());
                CardInstance ci = ec.state().card(ec.cardId());
                if (ci != null) {
                    resolved = CardModifierOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), actorRef(), ec.cardId(), ci, ec.ctx().def(ci.defId()), resolved, enemyCandidatesFor(actorRef()), ec.out(), ec.actor().value());
                }
                yield List.of(resolved);
            }
            case ANY_ONE -> {
                TargetRef chosen = ec.selection().requireOne();
                if (chosen instanceof TargetRef.Enemy || chosen instanceof TargetRef.Summon) {
                    TargetRef resolved = StatusOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), actorRef(), ec.cardId(), chosen, ec.out(), ec.actor().value());
                    CardInstance ci = ec.state().card(ec.cardId());
                    if (ci != null) {
                        resolved = CardModifierOps.resolveEnemyOneTarget(ec.state(), ec.ctx(), actorRef(), ec.cardId(), ci, ec.ctx().def(ci.defId()), resolved, enemyCandidatesFor(actorRef()), ec.out(), ec.actor().value());
                    }
                    yield List.of(resolved);
                }
                yield List.of(chosen);
            }
        };
    }

    private void applyDamage(TargetRef ref, int amount) {
        int finalAmount = amount;
        Rational multiplier = criticalAmountMultiplier(ref, "damage");
        if (multiplier.compareTo(Rational.ONE) > 0 && isCritical(ref, "damage")) {
            finalAmount = multiplyAndRound(finalAmount, multiplier);
            ec.out().add(new GameEvent.LogAppended(ec.actor().value() + " critical! damage x" + formatMultiplier(multiplier)));
        }

        DamageFlags flags = KeywordOps.damageFlags(
                ec.state(),
                ec.ctx(),
                actorRef(),
                ec.cardId(),
                ref
        );
        DamageOps.apply(
                ec.state(),
                ec.ctx(),
                ec.out(),
                actorRef(),
                ec.cardId(),
                ec.actor().value(),
                ref,
                finalAmount,
                flags
        );
    }

    private void applyHeal(TargetRef ref, int amount) {
        int finalAmount = amount;
        Rational multiplier = criticalAmountMultiplier(ref, "heal");
        if (multiplier.compareTo(Rational.ONE) > 0 && isCritical(ref, "heal")) {
            finalAmount = multiplyAndRound(finalAmount, multiplier);
            ec.out().add(new GameEvent.LogAppended(ec.actor().value() + " critical! heal x" + formatMultiplier(multiplier)));
        }
        HealOps.apply(
                ec.state(),
                ec.ctx(),
                ec.out(),
                actorRef(),
                ec.cardId(),
                ec.actor().value(),
                ref,
                finalAmount
        );
    }

    private Rational criticalAmountMultiplier(TargetRef target, String kind) {
        TargetRef source = actorRef();
        Rational multiplier = KeywordOps.criticalAmountMultiplier(
                ec.state(),
                ec.ctx(),
                source,
                ec.cardId(),
                target,
                kind
        );

        multiplier = PassiveOps.incomingCriticalAmountMultiplier(
                ec.state(),
                ec.ctx(),
                ec.out(),
                source,
                target,
                kind,
                multiplier,
                ec.actor().value()
        );
        multiplier = applyStatusIncomingCriticalAmountMultiplier(source, target, kind, multiplier);

        multiplier = applyStatusCriticalAmountMultiplier(source, target, kind, multiplier);
        return PassiveOps.criticalAmountMultiplier(
                ec.state(),
                ec.ctx(),
                ec.out(),
                source,
                target,
                kind,
                multiplier,
                ec.actor().value()
        );
    }

    private boolean isCritical(TargetRef target, String kind) {
        TargetRef source = actorRef();
        int chance = KeywordOps.criticalChancePercent(
                ec.state(),
                ec.ctx(),
                source,
                ec.cardId(),
                target,
                kind
        );
        chance = PassiveOps.criticalChancePercent(
                ec.state(),
                ec.ctx(),
                ec.out(),
                source,
                target,
                kind,
                chance,
                ec.actor().value()
        );
        chance = applyStatusCriticalChancePercent(source, target, kind, chance);

        chance = PassiveOps.incomingCriticalChancePercent(
                ec.state(),
                ec.ctx(),
                ec.out(),
                source,
                target,
                kind,
                chance,
                ec.actor().value()
        );
        chance = applyStatusIncomingCriticalChancePercent(source, target, kind, chance);
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


    private List<TargetRef> enemyCandidatesFor(TargetRef actor) {
        List<TargetRef> enemyCandidates = new ArrayList<>();
        if (actor instanceof TargetRef.Player) {
            ec.state().enemies().keySet().forEach(id -> enemyCandidates.add(TargetRef.ofEnemy(id)));
            ec.state().summons().values().forEach(s -> enemyCandidates.add(TargetRef.ofSummon(s.owner(), s.id())));
            return enemyCandidates;
        }
        ec.state().players().keySet().forEach(id -> enemyCandidates.add(TargetRef.ofPlayer(id)));
        return enemyCandidates;
    }

    private TargetRef actorRef() {
        if (ec.actor() != null && ec.state().enemy(new Ids.EnemyId(ec.actor().value())) != null) {
            return TargetRef.ofEnemy(new Ids.EnemyId(ec.actor().value()));
        }
        return TargetRef.ofPlayer(ec.actor());
    }


    private int applyStatusCriticalChancePercent(TargetRef source, TargetRef target, String kind, int baseChance) {
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
        int cur = baseChance;
        for (HookEntry it : collectStatusEntries(rt, source)) {
            if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
            int stacks = rt.stacks(it.owner(), it.statusId());
            if (stacks <= 0) continue;
            cur = ec.ctx().statusEffect(it.statusId()).onCriticalChancePercent(rt, it.owner(), source, target, kind, cur);
        }
        return Math.max(0, Math.min(100, cur));
    }

    private Rational applyStatusCriticalAmountMultiplier(TargetRef source, TargetRef target, String kind, Rational baseMultiplier) {
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
        Rational cur = Rational.max(Rational.ONE, baseMultiplier);
        for (HookEntry it : collectStatusEntries(rt, source)) {
            if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
            int stacks = rt.stacks(it.owner(), it.statusId());
            if (stacks <= 0) continue;
            cur = ec.ctx().statusEffect(it.statusId()).onCriticalAmountMultiplier(rt, it.owner(), source, target, kind, cur);
        }
        return Rational.max(Rational.ONE, cur);
    }


    private int applyStatusIncomingCriticalChancePercent(TargetRef source, TargetRef target, String kind, int baseChance) {
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
        int cur = baseChance;
        for (HookEntry it : collectStatusEntries(rt, target)) {
            if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
            int stacks = rt.stacks(it.owner(), it.statusId());
            if (stacks <= 0) continue;
            cur = ec.ctx().statusEffect(it.statusId()).onIncomingCriticalChancePercent(rt, it.owner(), source, target, kind, cur);
        }
        return Math.max(0, Math.min(100, cur));
    }

    private Rational applyStatusIncomingCriticalAmountMultiplier(TargetRef source, TargetRef target, String kind, Rational baseMultiplier) {
        StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
        Rational cur = Rational.max(Rational.ONE, baseMultiplier);
        for (HookEntry it : collectStatusEntries(rt, target)) {
            if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
            int stacks = rt.stacks(it.owner(), it.statusId());
            if (stacks <= 0) continue;
            cur = ec.ctx().statusEffect(it.statusId()).onIncomingCriticalAmountMultiplier(rt, it.owner(), source, target, kind, cur);
        }
        return Rational.max(Rational.ONE, cur);
    }

    private record HookEntry(StatusOwnerRef owner, String statusId, int priority) {}

    private List<HookEntry> collectStatusEntries(StatusRuntime rt, TargetRef owner) {
        List<HookEntry> entries = new ArrayList<>();

        var ownerChar = StatusOwnerRef.of(owner);
        for (String k : rt.statusMap(ownerChar).keySet()) {
            entries.add(new HookEntry(ownerChar, k, ec.ctx().hasStatusDef(k) ? ec.ctx().statusDef(k).priority() : Integer.MAX_VALUE));
        }

        CombatState cs = ec.state().combat();
        if (cs != null) {
            var ownerFaction = StatusOwnerRef.of(CombatState.factionOf(owner));
            for (String k : rt.statusMap(ownerFaction).keySet()) {
                entries.add(new HookEntry(ownerFaction, k, ec.ctx().hasStatusDef(k) ? ec.ctx().statusDef(k).priority() : Integer.MAX_VALUE));
            }
        }

        entries.sort(Comparator.comparingInt(HookEntry::priority));
        return entries;
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

    private static String formatMultiplier(Rational multiplier) {
        if (multiplier.getDenominator() == 1L) {
            return Long.toString(multiplier.getNumerator());
        }
        return multiplier.getNumerator() + "/" + multiplier.getDenominator();
    }

    private static int multiplyAndRound(int amount, Rational multiplier) {
        BigInteger numerator = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(multiplier.getNumerator()));
        BigInteger denominator = BigInteger.valueOf(multiplier.getDenominator());

        BigInteger[] divRem = numerator.divideAndRemainder(denominator);
        BigInteger quotient = divRem[0];
        BigInteger absTwiceRem = divRem[1].abs().shiftLeft(1);
        if (absTwiceRem.compareTo(denominator) >= 0) {
            quotient = quotient.add(BigInteger.valueOf(numerator.signum() >= 0 ? 1L : -1L));
        }
        return quotient.intValueExact();
    }


    private PlayerState actor() {
        PlayerState me = ec.state().player(ec.actor());
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());
        return me;
    }

    private int actorAttackPower() {
        PlayerState actor = actor();
        return actor.attackPower() + EquipmentCombatOps.attackPowerBonus(actor, ec.ctx());
    }
}
