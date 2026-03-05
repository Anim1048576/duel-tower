package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.ZoneOps;
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
public class Tig002_Card implements CardBlueprint {
    @Override public String id() { return "Tig002_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "맛난 생선",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        아군 1명의 체력을 자신의 {치유력}만큼 회복시킨다.
                        극복이 3이상인 경우, 1장 드로우 한다.
                        """
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        return new EffectOps(ec).validateTarget(Target.ALLY_ONE);
    }

    @Override
    public void resolve(EffectContext ec) {
        EffectOps ops = new EffectOps(ec);
        PlayerState me = ec.state().player(ec.actor());
        int overcome = TigEffectSupport.overcome(me);

        ops.heal(Target.ALLY_ONE, me.healPower());
        if (overcome >= 3) ZoneOps.drawWithRefill(ec.state(), ec.ctx(), me, 1, ec.out());
    }
}
