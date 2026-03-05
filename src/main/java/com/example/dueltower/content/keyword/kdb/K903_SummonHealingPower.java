package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 치유력 n]
 * 이 [스킬 카드]가 갖는 치유력입니다.
 */
@Component
public class K903_SummonHealingPower implements KeywordBlueprint {

    public static final String ID = "치유력";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "치유력",
                true,
                """
                        이 [스킬 카드]가 갖는 치유력입니다.
                        """
        );
    }
}
