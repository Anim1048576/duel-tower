package com.example.dueltower.content.card.cdb;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.BoardObjectKind;
import com.example.dueltower.content.card.model.playspec.BoardObjectRelation;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.SelectBoardObjectsRequirement;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.EquipmentCombatOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.card.ReactionEffectContext;
import com.example.dueltower.engine.core.effect.card.ReactiveCardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.ReactionTrigger;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class C005_EmergencyAttack implements CardBlueprint, ReactiveCardEffect {
    @Override public String id() { return "C005"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "긴급 공격",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        적 1명에게 {공격력}만큼 피해를 준다.
                        반응: 적의 공격으로 자신이 피해를 받은 직후, 비용 없이 방금 공격한 적에게 {공격력/2}만큼 피해를 준다.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return new CardPlaySpec(
                TargetSpec.none(),
                List.of(new SelectBoardObjectsRequirement(
                        1,
                        1,
                        List.of(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON),
                        BoardObjectRelation.HOSTILE,
                        null,
                        false
                ))
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        return new EffectOps(ec).validateTarget(Target.ENEMY_ONE);
    }

    @Override
    public void resolve(EffectContext ec) {
        new EffectOps(ec).damageWithActorAttack(Target.ENEMY_ONE);
    }

    @Override
    public boolean canReact(ReactionEffectContext rc) {
        if (rc == null || rc.reaction() == null) return false;
        if (rc.reaction().trigger() != ReactionTrigger.AFTER_ENEMY_ATTACK_DAMAGED_SELF) return false;
        if (!rc.actor().equals(rc.reaction().ownerPlayerId())) return false;
        if (!(rc.reaction().subject() instanceof TargetRef.Player subject) || !subject.id().equals(rc.actor())) return false;
        if (!(rc.reaction().source() instanceof TargetRef.Enemy source)) return false;
        EnemyState enemy = rc.state().enemy(source.id());
        return enemy != null && enemy.hp() > 0;
    }

    @Override
    public void resolveReaction(ReactionEffectContext rc) {
        if (!canReact(rc)) {
            throw new IllegalStateException("reaction source is no longer valid");
        }

        TargetRef source = rc.reaction().source();
        int damage = Math.floorDiv(attackPower(rc), 2);
        DamageOps.apply(
                rc.state(),
                rc.ctx(),
                rc.out(),
                rc.actorRef(),
                rc.cardId(),
                rc.sourceLabel(),
                source,
                damage,
                KeywordOps.damageFlags(rc.state(), rc.ctx(), rc.actorRef(), rc.cardId(), source)
        );
    }

    private static int attackPower(ReactionEffectContext rc) {
        PlayerState player = rc.state().player(rc.actor());
        if (player == null) {
            throw new IllegalStateException("missing player: " + rc.actor().value());
        }
        return player.attackPower() + EquipmentCombatOps.attackPowerBonus(player, rc.ctx());
    }
}
