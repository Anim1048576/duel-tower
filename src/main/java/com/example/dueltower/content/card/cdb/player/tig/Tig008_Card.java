package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.status.sdb.player.tig.Tig202_Status;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class Tig008_Card implements CardBlueprint {
    @Override public String id() { return "Tig008_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "오버 드라이브",
                CardType.SKILL,
                3,
                Map.of(
                        K003_Installed.ID, 1
                ),
                Zone.FIELD,
                false,
                """
                        패를 1장 버리고 설치한다, 자신의 공격력이 4 상승한다.
                        극복이 3이상인 경우, 설치시 적 하나에게 {공격력}+{극복}피해를 입힌다.
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

        int overcome = TigEffectSupport.overcome(me);
        if (overcome >= 3) ops.damage(Target.ENEMY_ONE, me.attackPower() + overcome);
    }

    @Override
    public void onEnterField(EffectContext ec, Ids.CardInstId sourceCardId) {
        new EffectOps(ec).addStatus(Target.SELF, Tig202_Status.ID, 4);
    }

    @Override
    public void onLeaveField(EffectContext ec, Ids.CardInstId sourceCardId) {
        PlayerState me = ec.state().player(ec.actor());
        int current = me.status(Tig202_Status.ID);
        if (current > 0) new EffectOps(ec).addStatus(Target.SELF, Tig202_Status.ID, -Math.min(4, current));
    }
}
