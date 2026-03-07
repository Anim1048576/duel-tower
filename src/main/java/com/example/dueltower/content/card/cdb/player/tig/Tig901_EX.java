package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class Tig901_EX implements CardBlueprint {

    @Override public String id() { return "Tig901_EX"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new CardDefId(id()),
                "맹호류 결착",
                CardType.EX,
                3,
                Map.of(),
                Zone.EX,
                false,
                """
                        적 둘에게 {공격력}+[극복] 피해를 입히고 1장 드로우한다.
                        또는, 적 하나에게 {공격력}+[극복] 피해를 2번 입히고 1장 드로우한다.
                        [극복]이 3이상인 경우, 공격 횟수를 1 증가시킨다.
                        """
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        List<TargetRef> targets = ec.selection() == null ? List.of() : ec.selection().targets();
        if (targets == null || targets.isEmpty() || targets.size() > 2) return List.of("select one or two enemies");
        for (TargetRef t : targets) {
            if (!(t instanceof TargetRef.Enemy) && !(t instanceof TargetRef.Summon)) return List.of("enemy/summon target required");
        }
        return List.of();
    }

    @Override
    public void resolve(EffectContext ec) {
        PlayerState me = ec.state().player(ec.actor());
        int overcome = TigEffectSupport.overcome(me);
        int damage = me.attackPower() + overcome;

        List<TargetRef> selected = ec.selection().targets();
        int hits = (selected.size() == 1 ? 2 : 1) + (overcome >= 3 ? 1 : 0);
        new EffectOps(ec).damageSelected(selected, damage, hits);

        ZoneOps.drawWithRefill(ec.state(), ec.ctx(), me, 1, ec.out());
    }
}
