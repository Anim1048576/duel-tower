package com.example.dueltower.content.card.cdb.player.tig;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.DiscardFilter;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.content.card.model.playspec.FieldCardFilter;
import com.example.dueltower.content.card.model.playspec.FieldCardSelectionScope;
import com.example.dueltower.content.card.model.playspec.SelectFieldCardsRequirement;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Tig006_Card implements CardBlueprint {
    @Override public String id() { return "Tig006_Card"; }

    @Override
    public CardDefinition definition() {
        return new CardDefinition(
                new Ids.CardDefId(id()),
                "크게 베기!!",
                CardType.SKILL,
                2,
                Map.of(),
                Zone.GRAVE,
                false,
                """
                        패 1장을 버리고 설치된 카드를 3장 까지 파괴한다.
                        """
        );
    }

    @Override
    public CardPlaySpec playSpec() {
        return new CardPlaySpec(
                TargetSpec.none(),
                List.of(
                        new DiscardFromHandRequirement(1, true, DiscardFilter.ANY),
                        new SelectFieldCardsRequirement(
                                0,
                                3,
                                FieldCardSelectionScope.ALL_PLAYER_FIELDS,
                                FieldCardFilter.INSTALLED_ONLY,
                                true
                        )
                )
        );
    }

    @Override
    public List<String> validate(EffectContext ec) {
        List<String> errors = new ArrayList<>();
        TigEffectSupport.validateSingleDiscardSelection(ec, ec.state().player(ec.actor()), errors);
        TigEffectSupport.validateInstalledCardSelection(ec, 0, 3, errors);
        return errors;
    }

    @Override
    public void resolve(EffectContext ec) {
        var me = ec.state().player(ec.actor());
        if (!TigEffectSupport.discardSelectedOrAbort(ec, me)) return;
        TigEffectSupport.destroySelectedInstalledCards(ec);
    }
}
