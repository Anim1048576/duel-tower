package com.example.dueltower.content.keyword.kdb;

import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.core.effect.keyword.MoveCtx;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.Zone;
import org.springframework.stereotype.Component;

/**
 * [키워드 : 소환]
 * 이 [스킬 카드]는 사용했을 경우, [묘지]로 보내지지 않고 필드에 남습니다.
 * 별도의 [체력], [공격력], [치유력]을 가지고, 공격 카드의 대상으로 지정될 수 있습니다.
 * 한 턴에 각각 1번씩, [액션 n]의 효과를 선언하여 적용할 수 있습니다. (n은 소모할 코스트)
 *
 * NOTE: 소환 유닛의 전용 능력치/타겟팅/액션 선언 처리 자체는
 * 전투 대상 모델 확장이 필요하므로 별도 단계에서 구현합니다.
 */
@Component
public class K004_Summon implements KeywordBlueprint {

    public static final String ID = "소환";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public KeywordDefinition definition() {
        return new KeywordDefinition(
                ID,
                "소환",
                false,
                """
                        이 [스킬 카드]는 사용했을 경우, [묘지]로 보내지지 않고 필드에 남습니다.
                        별도의 [체력], [공격력], [치유력]을 가지고, 공격 카드의 대상으로 지정될 수 있습니다.
                        한 턴에 각각 1번씩, [액션 n]의 효과를 선언하여 적용할 수 있습니다. (n은 소모할 코스트)
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
