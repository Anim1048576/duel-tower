package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.core.effect.keyword.MoveCtx;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 설치]
 * 이 [스킬 카드]는 사용했을 경우, [묘지]로 보내지지 않고 필드에 남습니다.
 * [스킬 카드]의 효과는 필드에 남아있는동안 적용됩니다.
 */
@Component
public class K003_Installed implements KeywordBlueprint {

    public static final String ID = "설치";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "설치",
                false,
                """
                        이 [스킬 카드]는 사용했을 경우, [묘지]로 보내지지 않고 필드에 남습니다.
                        [스킬 카드]의 효과는 필드에 남아있는동안 적용됩니다.
                        """
        );
    }

    @Override
    public Zone overrideMoveDestination(KeywordRuntime rt, MoveCtx c, Zone currentTo) {
        if (rt.value() == 0) return currentTo;

        // 사용 후 기본 이동지(GRAVE)를 FIELD로 변경
        if (c.reason() == MoveReason.PLAY && currentTo == Zone.GRAVE) {
            return Zone.FIELD;
        }
        return currentTo;
    }
}
