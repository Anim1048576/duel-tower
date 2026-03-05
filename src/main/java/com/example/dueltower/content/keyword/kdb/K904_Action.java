package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 액션 n]
 * 1턴에 1번, 코스트를 n 소모하여 해당 효과를 적용할 수 있습니다.
 */
@Component
public class K904_Action implements KeywordBlueprint {

    public static final String ID = "액션";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "액션",
                true,
                """
                        1턴에 1번, 코스트를 n 소모하여 해당 효과를 적용할 수 있습니다.
                        """
        );
    }
}
