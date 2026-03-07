package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

import java.util.List;

import java.util.Map;

@Component
public class Tig001_Card implements CardBlueprint {
    @Override public String id() { return "Tig001_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "사슴류 베기",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        적 1명에게 공격력+{극복} 피해를 입힌다.
                        극복이 3이상인 경우, 설치된 카드를 1장 파괴할 수 있다.
                        """
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        return new EffectOps(ec).validateTarget(Target.ENEMY_ONE);
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        PlayerState me = ec.state().player(ec.actor());
        int overcome = TigEffectSupport.overcome(me);

        ops.damageWithActorAttackPlus(overcome, Target.ENEMY_ONE);
        if (TigEffectSupport.isOvercome3Plus(me)) TigEffectSupport.destroyInstalledCards(ec, 1);
    }
}
