package com.example.dueltower.content.card.cdb;

import com.example.dueltower.content.card.CardBlueprint;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EX - 붕대 감기 [코스트: 1]
 * 아군 1명의 체력을 {치유력}*3/4 만큼 회복한다.
 * 자신의 패가 1장 이하라면, [스킬 카드]를 1장 뽑는다.
 */
@Component
public class EX901_BandageWrap implements CardBlueprint {

    @Override public String id() { return "EX901"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "붕대 감기",
                CardType.EX,
                1,
                List.of(),
                Zone.EX,
                false,
                """
                        아군 1명의 체력을 {치유력}*3/4 만큼 회복한다.
                        자신의 패가 1장 이하라면, [스킬 카드]를 1장 뽑는다.
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
        if (me == null) throw new IllegalStateException("missing player: " + ec.actor().value());

        int heal = me.healPower() * 3 / 4;
        ops.heal(Target.ALLY_ONE, heal);

        if (me.hand().size() <= 1) {
            ZoneOps.drawWithRefill(ec.state(), ec.ctx(), me, 1, ec.out());
        }
    }
}
