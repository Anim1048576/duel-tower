package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.DiscardCtx;
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

    @Override
    public String id() {
        return "부동";
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                id(),
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
        return true;
    }

    @Override
    public void validateDiscard(KeywordRuntime rt, DiscardCtx c, List<String> errors) {
        errors.add("cannot discard a '부동' card: " + c.cardId().value());
    }
}
