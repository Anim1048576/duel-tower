package com.example.dueltower.content.card;

import com.example.dueltower.engine.model.*;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;

@Service
public class CardService {
    public List<CardDefinition> list() {
        return List.of(
                new CardDefinition(new Ids.CardDefId("C001"), "기본 공격", CardType.SKILL, 1, EnumSet.noneOf(Keyword.class), "DMG", Zone.GRAVE, false, "적 1명에게 {공격력} 만큼의 대미지를 준다."),
                new CardDefinition(new Ids.CardDefId("C002"), "기본 치유", CardType.SKILL, 1, EnumSet.noneOf(Keyword.class), "RECOVERY", Zone.GRAVE, false, "아군 1명의 체력을 {치유력} 만큼 회복한다."),
                new CardDefinition(new Ids.CardDefId("C003"), "기본 방어", CardType.SKILL, 1, EnumSet.noneOf(Keyword.class), "SHIELD", Zone.GRAVE, false, "자신은 {치유력} 만큼의 [보호]를 얻는다."),
                new CardDefinition(new Ids.CardDefId("C004"), "기본 저주", CardType.SKILL, 2, EnumSet.noneOf(Keyword.class), "AVOID", Zone.GRAVE, false, "적 1명에게 {공격력} 만큼의 [고통]을 부여한다.")
        );
    }
}