package com.example.dueltower.content.card;

import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Keyword;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;

@Service
public class CardService {
    public List<CardDefinition> list() {
        return List.of(
                new CardDefinition(new Ids.CardDefId("C001"), "기본 공격", 1, EnumSet.noneOf(Keyword.class), "DMG", false),
                new CardDefinition(new Ids.CardDefId("C002"), "기본 방어", 1, EnumSet.noneOf(Keyword.class), "GUARD", false)
        );
    }
}