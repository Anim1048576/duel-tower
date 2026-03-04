package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 충전 n]
 * 그 턴중, 자신이 n개 이상의 [스킬 카드]를 사용했었을 때 추가 효과를 얻습니다.
 *
 * NOTE: 실제 추가 효과는 카드 효과(CardEffect) 구현에서
 * 플레이어의 cardsPlayedThisTurn 및 키워드 값(n)을 확인해 분기합니다.
 */
@Component
public class K013_Charge implements KeywordBlueprint {

    public static final String ID = "충전";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "충전",
                true,
                """
                        그 턴중, 자신이 n개 이상의 [스킬 카드]를 사용했었을 때 추가 효과를 얻습니다.
                        """
        );
    }
}
