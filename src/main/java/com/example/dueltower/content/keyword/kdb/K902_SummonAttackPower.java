package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 공격력 n]
 * 이 [스킬 카드]가 갖는 공격력입니다.
 */
@Component
public class K902_SummonAttackPower implements KeywordBlueprint {

    public static final String ID = "공격력";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "공격력",
                true,
                """
                        이 [스킬 카드]가 갖는 공격력입니다.
                        """
        );
    }
}
