package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S002_Regeneration;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Nameless002_Card implements CardBlueprint {
    @Override public String id() { return "Nameless002_Card"; }

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
                "유니버스",
                1,
                """
                        대상 1명을 지정한다.
                        아군이면 {치유력}/2 만큼 [재생]을 부여한다.
                        적이면 {공격력} 만큼 대미지를 준다.
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
            ops.addStatus(Target.ANY_ONE, S002_Regeneration.ID, ops.actorHealPower() / 2);
            return;
        }
        ops.damage(Target.ANY_ONE, ops.actorAttackPower());
    }
}
