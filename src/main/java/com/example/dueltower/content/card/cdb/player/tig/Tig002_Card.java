package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
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
                        극복이 3이상인 경우, [스킬 카드]를 1장 뽑는다.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return new CardPlaySpec(
                TargetSpec.required(Target.ALLY_ONE),
                List.of()
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
        if (TigEffectSupport.isOvercome3Plus(me)) ZoneOps.drawWithRefill(ec.state(), ec.ctx(), me, 1, ec.out());
    }
}
