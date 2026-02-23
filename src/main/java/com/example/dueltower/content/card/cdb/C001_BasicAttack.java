package com.example.dueltower.content.card.cdb;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.CardEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class C001_BasicAttack implements CardEffect {
    @Override public String id() { return "C001"; }

    @Override
    public void resolve(GameState state, EngineContext ctx, PlayerId playerId, CardInstId cardId, List<GameEvent> out) {
        out.add(new GameEvent.LogAppended(playerId.value() + " uses C001 (basic attack)"));
        // TODO: 데미지/타겟 시스템 붙으면 여기서 처리
    }
}