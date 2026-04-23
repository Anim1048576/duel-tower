package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

/**
 * [키워드: 유언 n]
 * 효과로 버려졌을 경우, 그 효과 처리 후에, n만큼의 행동력을 지불하는 것으로 기재된 효과를 발동합니다.
 * 이 키워드의 효과는 동일한 타이밍에는 1번만 발동할 수 있습니다.
 *
 * NOTE: 실제 후보 수집 / 선택 / 비용 지불 / payload 실행은 별도 엔진 로직에서 처리한다.
 */
@Component
public class K014_LastWords implements KeywordBlueprint {

    public static final String ID = "유언";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "유언",
                true,
                """
                        효과로 버려졌을 경우, 그 효과 처리 후에, n만큼의 행동력을 지불하는 것으로 기재된 효과를 발동합니다.
                        이 키워드의 효과는 동일한 타이밍에는 1번만 발동할 수 있습니다.
                        """
        );
    }
}
