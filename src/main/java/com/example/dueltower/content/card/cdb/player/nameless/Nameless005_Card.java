package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Nameless005_Card implements CardBlueprint {
    @Override public String id() { return "Nameless005_Card"; }

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
                "보이드",
                3,
                """
                        대상 1명에게 {공격력}+{치유력} 피해를 준다.
                        [취약]을 3 부여한다.
                        그 후, 패에서 카드 1장을 버린다.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return NamelessEffectSupport.anyOneWithDiscardPlaySpec();
    }

    @Override
    public List<String> validate(EffectContext ec) {
        List<String> errors = new ArrayList<>(NamelessEffectSupport.validateAnyOneTarget(ec));
        PlayerState me = ec.state().player(ec.actor());
        NamelessEffectSupport.validateSingleDiscardSelection(ec, me, errors);
        return errors;
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        PlayerState me = ec.state().player(ec.actor());

        ops.damage(Target.ANY_ONE, ops.actorAttackPower() + ops.actorHealPower());
        ops.addStatus(Target.ANY_ONE, S106_Vulnerable.ID, 3);
        NamelessEffectSupport.discardSelectedOrAbort(ec, me);
    }
}
