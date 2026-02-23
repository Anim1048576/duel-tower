package com.example.dueltower.content.card;

import com.example.dueltower.engine.core.CardEffect;
import com.example.dueltower.engine.core.CardEffectResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CardEffectRegistry implements CardEffectResolver {

    private final Map<String, CardEffect> map;

    public CardEffectRegistry(List<CardEffect> effects) {
        Map<String, CardEffect> m = new HashMap<>();
        for (CardEffect e : effects) {
            CardEffect prev = m.put(e.id(), e);
            if (prev != null) {
                throw new IllegalStateException("duplicate CardEffect id: " + e.id());
            }
        }
        this.map = Map.copyOf(m);
    }

    @Override
    public boolean exists(String effectId) {
        return map.containsKey(effectId);
    }

    @Override
    public CardEffect resolve(String effectId) {
        CardEffect e = map.get(effectId);
        if (e == null) throw new IllegalArgumentException("missing CardEffect: " + effectId);
        return e;
    }
}