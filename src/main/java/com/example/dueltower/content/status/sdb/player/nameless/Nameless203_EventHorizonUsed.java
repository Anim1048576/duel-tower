package com.example.dueltower.content.status.sdb.player.nameless;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.StatusKind;
import com.example.dueltower.engine.model.StatusScope;
import com.example.dueltower.engine.model.StatusVisibility;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class Nameless203_EventHorizonUsed implements StatusBlueprint {
    public static final String ID = "EVENT_HORIZON_USED";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String contentOwner() {
        return ContentOwnerIds.NAMELESS;
    }

    @Override
    public StatusDefinition definition() {
        return new StatusDefinition(
                id(),
                "사건의 지평선 사용됨",
                StatusKind.NEUTRAL,
                StatusScope.CHARACTER,
                Set.of(),
                100,
                true,
                "중력 특이점 증강 3의 세션 중 1회 사용 여부를 기록하는 구현용 상태.",
                StatusVisibility.IMPLEMENTATION
        );
    }
}
