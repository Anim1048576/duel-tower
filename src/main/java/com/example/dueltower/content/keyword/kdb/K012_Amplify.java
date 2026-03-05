package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 증강 n]
 * n만큼의 [행동력]을 추가로 지불하는 것으로 추가 효과를 얻습니다.
 *
 * NOTE: 실제 증강 효과/지불 방식은 카드 효과(CardEffect)에서
 * 키워드 값(n)과 현재 AP를 확인해 분기합니다.
 */
@Component
public class K012_Amplify implements KeywordBlueprint {

    public static final String ID = "증강";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "증강",
                true,
                """
                        n만큼의 [행동력]을 추가로 지불하는 것으로 추가 효과를 얻습니다.
                        """
        );
    }
}
