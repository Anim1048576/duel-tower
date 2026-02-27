package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.DiscardCtx;
import com.example.dueltower.engine.core.effect.keyword.DiscardReason;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [키워드 : 부동]
 * 이 [스킬 카드]는 패에 있는 경우 버릴 수 없습니다.
 * 패가 6장을 초과해서 카드를 버릴 경우, 패에 [부동] 카드 뿐이라면 버리지 않습니다.
 */
@Component
public class K006_Immovable implements KeywordBlueprint {

    public static final String ID = "부동";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "부동",
                false,
                """
                        이 [스킬 카드]는 패에 있는 경우 버릴 수 없습니다.
                        패가 6장을 초과해서 카드를 버릴 경우, 패에 [부동] 카드 뿐이라면 버리지 않습니다.
                        """
        );
    }

    @Override
    public boolean blocksDiscard(KeywordRuntime rt, DiscardCtx c) {
        // 값이 0이면 비활성 취급, 그 외는 활성
        return rt.value() != 0;
    }

    @Override
    public void validateDiscard(KeywordRuntime rt, DiscardCtx c, List<String> errors) {
        if (rt.value() == 0) return;

        // 이유별 메시지 분기하고 싶으면 여기서 처리
        DiscardReason r = c.reason();
        if (r == DiscardReason.HAND_LIMIT) {
            errors.add("부동 카드는 손패 제한으로도 버릴 수 없습니다.");
        } else {
            errors.add("부동 카드는 버릴 수 없습니다.");
        }
    }
}
