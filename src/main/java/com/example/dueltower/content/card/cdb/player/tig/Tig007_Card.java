package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.status.sdb.S004_Evasion;
import com.example.dueltower.content.status.sdb.player.tig.Tig203_Status;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Target;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Tig007_Card implements CardBlueprint {
    @Override public String id() { return "Tig007_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "위기 극복",
                CardType.SKILL,
                3,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        다음 자신의 턴 개시시까지 자신은 [회피]를 [극복]만큼 얻는다.
                        """
        );
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        PlayerState me = ec.state().player(ec.actor());
        int amount = TigEffectSupport.overcome(me);
        if (amount <= 0) return;

        ops.addStatus(Target.SELF, S004_Evasion.ID, amount);
        ops.addStatus(Target.SELF, Tig203_Status.ID, amount);
    }
}
