package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 체력 n]
 * 이 수치가 0이 될 시, 이 [스킬 카드]는 파괴됩니다.
 */
@Component
public class K901_SummonHp implements KeywordBlueprint {

    public static final String ID = "체력";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "체력",
                true,
                """
                        이 수치가 0이 될 시, 이 [스킬 카드]는 파괴됩니다.
                        """
        );
    }
}
