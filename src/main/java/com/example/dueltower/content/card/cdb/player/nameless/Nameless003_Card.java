package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S001_Shield;
import com.example.dueltower.content.status.sdb.S104_Destruction;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Nameless003_Card implements CardBlueprint {
    @Override public String id() { return "Nameless003_Card"; }

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
                "코스모스",
                2,
                """
                        대상 1명을 지정한다.
                        아군이면 {치유력}*2 만큼 [보호]를 부여한다.
                        적이면 {공격력}/2 만큼 [파괴]를 부여한다.
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
            ops.addStatus(Target.ANY_ONE, S001_Shield.ID, ops.actorHealPower() * 2);
            return;
        }
        ops.addStatus(Target.ANY_ONE, S104_Destruction.ID, ops.actorAttackPower() / 2);
    }
}
