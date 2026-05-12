package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Nameless001_Card implements CardBlueprint {
    @Override public String id() { return "Nameless001_Card"; }

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
                "스페이스",
                1,
                """
                        대상 1명을 지정한다.
                        아군이면 {치유력} 만큼 회복한다.
                        적이면 {공격력}/2 만큼 [고통]을 부여한다.
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
            ops.heal(Target.ANY_ONE, ops.actorHealPower());
            return;
        }
        ops.addStatus(Target.ANY_ONE, S101_Pain.ID, ops.actorAttackPower() / 2);
    }
}
