package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.BoardObjectKind;
import com.example.dueltower.content.card.model.playspec.BoardObjectRelation;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.DiscardFilter;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.content.card.model.playspec.SelectBoardObjectsRequirement;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class Tig003_Card implements CardBlueprint {
    @Override public String id() { return "Tig003_Card"; }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.TIG;
    }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "여우류 신속베기",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        패를 1장 버리고, 1D6을 굴려 4이상의 값이 나온 경우 3장 드로우 한다.
                        극복이 3이상인 경우, 추가로 적 1명에게 [취약]을 3 부여한다.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return new CardPlaySpec(
                TargetSpec.none(),
                List.of(
                        new SelectBoardObjectsRequirement(
                                1,
                                1,
                                List.of(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON),
                                BoardObjectRelation.HOSTILE,
                                null,
                                false
                        ),
                        new DiscardFromHandRequirement(1, true, DiscardFilter.ANY)
                )
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

        if (!TigEffectSupport.requireDiscardOrAbort(ec, me, id())) return;

        int roll = TigEffectSupport.rollD6(ec);
        TigEffectSupport.log(ec, id() + " rolled 1d6=" + roll);
        if (roll >= 4) ZoneOps.drawWithRefill(ec.state(), ec.ctx(), me, 3, ec.out());

        if (TigEffectSupport.isOvercome3Plus(me)) {
            ops.addStatus(Target.ENEMY_ONE, S106_Vulnerable.ID, 3);
        }
    }
}
