package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Nameless004_Card implements CardBlueprint {
    @Override public String id() { return "Nameless004_Card"; }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.NAMELESS;
    }

    @Override
    public Integer maxDeckCopies() {
        return NamelessEffectSupport.MAX_DECK_COPIES;
    }

    @Override
    public CardDefinition definition() {
        return NamelessEffectSupport.skillDefinition(
                id(),
                "카오스",
                2,
                """
                        대상 1명을 지정한다.
                        아군이면 {치유력}+{공격력}/2 만큼 회복한다.
                        적이면 {공격력}+{치유력}/2 만큼 대미지를 준다.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return NamelessEffectSupport.anyOnePlaySpec();
    }

    @Override
    public List<String> validate(EffectContext ec) {
        return NamelessEffectSupport.validateAnyOneTarget(ec);
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        if (NamelessEffectSupport.selectedTargetIsAlly(ec)) {
            ops.heal(Target.ANY_ONE, ops.actorHealPower() + ops.actorAttackPower() / 2);
            return;
        }
        ops.damage(Target.ANY_ONE, ops.actorAttackPower() + ops.actorHealPower() / 2);
    }
}
