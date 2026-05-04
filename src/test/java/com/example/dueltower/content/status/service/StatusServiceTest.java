package com.example.dueltower.content.status.service;

import com.example.dueltower.content.status.sdb.S901_InstalledFieldBuff;
import com.example.dueltower.content.status.sdb.S902_SummonFieldAura;
import com.example.dueltower.content.status.sdb.player.tig.Tig202_Status;
import com.example.dueltower.content.status.sdb.player.tig.Tig203_Status;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.StatusKind;
import com.example.dueltower.engine.model.StatusScope;
import com.example.dueltower.engine.model.StatusVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class StatusServiceTest {

    private static final Set<String> HIDDEN_STATUS_IDS = Set.of(
            Tig202_Status.ID,
            Tig203_Status.ID,
            S901_InstalledFieldBuff.ID,
            S902_SummonFieldAura.ID
    );

    @Autowired
    private StatusService statusService;

    @Test
    @DisplayName("8개 인자 StatusDefinition은 기본적으로 공개 상태다")
    void eightArgumentStatusDefinitionDefaultsToPublic() {
        StatusDefinition definition = new StatusDefinition(
                "TEST_STATUS",
                "테스트 상태",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                Set.of(),
                1,
                false,
                "테스트 상태입니다."
        );

        assertEquals(StatusVisibility.PUBLIC, definition.visibility());
        assertTrue(definition.publicVisible());
    }

    @Test
    @DisplayName("상태 기본 목록은 공개 상태만 반환한다")
    void listReturnsPublicStatusesOnly() {
        Set<String> listedIds = statusService.list().stream()
                .map(StatusDefinition::id)
                .collect(Collectors.toSet());

        assertTrue(statusService.list().stream().allMatch(StatusDefinition::publicVisible));
        assertFalse(listedIds.contains(Tig202_Status.ID));
        assertFalse(listedIds.contains(Tig203_Status.ID));
        assertFalse(listedIds.contains(S901_InstalledFieldBuff.ID));
        assertFalse(listedIds.contains(S902_SummonFieldAura.ID));
    }

    @Test
    @DisplayName("숨겨진 상태는 전체 목록과 명시 조회 및 엔진 맵에서 접근 가능하다")
    void hiddenStatusesRemainAccessibleFromExplicitLookupsAndMaps() {
        Set<String> allIds = statusService.listAll().stream()
                .map(StatusDefinition::id)
                .collect(Collectors.toSet());

        assertTrue(allIds.containsAll(HIDDEN_STATUS_IDS));

        StatusDefinition tig202 = statusService.get(Tig202_Status.ID);
        StatusDefinition s901 = statusService.get(S901_InstalledFieldBuff.ID);

        assertNotNull(tig202);
        assertNotNull(s901);
        assertEquals(StatusVisibility.IMPLEMENTATION, tig202.visibility());
        assertEquals(StatusVisibility.TEST, s901.visibility());
        assertTrue(statusService.defsMap().keySet().containsAll(HIDDEN_STATUS_IDS));
        assertTrue(statusService.effectsMap().keySet().containsAll(HIDDEN_STATUS_IDS));
    }
}
