package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
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
public class Tig004_Card implements CardBlueprint {
    @Override public String id() { return "Tig004_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "이도류 연격!!",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        패를 1장 버리고 적 하나에게 공격력+{극복}피해를 입힌다.
                        극복이 3이상인 경우, 해당 공격은 2회로 변경된다.
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

        if (!TigEffectSupport.discardOneFromHandExcludingSource(ec, me)) {
            TigEffectSupport.log(ec, id() + ": discard failed, no card to discard");
            return;
        }

        int amount = me.attackPower() + TigEffectSupport.overcome(me);
        int hits = TigEffectSupport.overcome(me) >= 3 ? 2 : 1;
        for (int i = 0; i < hits; i++) ops.damage(Target.ENEMY_ONE, amount);
    }
}
