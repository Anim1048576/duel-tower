package com.example.dueltower.lab.service;

import com.example.dueltower.lab.dto.LabEffectProbeRequest;
import com.example.dueltower.lab.dto.LabEffectProbeResponse;
import com.example.dueltower.lab.dto.LabProbeActorDto;
import com.example.dueltower.lab.dto.LabProbeSelectionDto;
import com.example.dueltower.lab.dto.LabProbeTargetDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@SpringBootTest
@ActiveProfiles("test")
class LabEffectProbeServiceTest {

    @Autowired
    private LabEffectProbeService service;

    @Test
    @DisplayName("공격력 기반 피해 카드는 enemy hp를 감소시키고 상태 변화를 반영한다")
    void probeShouldApplyAttackPowerDamageToEnemy() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "C001",
                actor(7, 5, 20, 20, Map.of("PAIN", 4)),
                target("ENEMY", "dummy_enemy", 30, 30, Map.of("SHIELD", 3)),
                selection("ENEMY", "dummy_enemy"),
                12345L,
                false
        ));

        assertTrue(response.valid());
        assertTrue(response.resolved());
        assertEquals(30, response.before().targets().get(0).hp());
        assertEquals(26, response.after().targets().get(0).hp());
        assertEquals(4, response.before().actor().statuses().get("PAIN"));
        assertEquals(3, response.before().targets().get(0).statuses().get("SHIELD"));
        assertFalse(response.after().targets().get(0).statuses().containsKey("SHIELD"));
        assertEquals(-4, response.changes().targets().get(0).hpChange());
        assertEquals("SHIELD", response.changes().targets().get(0).statusChanges().get(0).statusId());
        assertEquals(3, response.changes().targets().get(0).statusChanges().get(0).before());
        assertEquals(0, response.changes().targets().get(0).statusChanges().get(0).after());
    }

    @Test
    @DisplayName("치유력 기반 회복 카드는 actor hp를 회복한다")
    void probeShouldApplyHealPowerToActor() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "C002",
                actor(2, 5, 10, 20, Map.of()),
                target("PLAYER", "lab_actor", 10, 20, Map.of()),
                selection("PLAYER", "lab_actor"),
                12345L,
                false
        ));

        assertTrue(response.valid());
        assertTrue(response.resolved());
        assertEquals(10, response.before().actor().hp());
        assertEquals(15, response.after().actor().hp());
        assertEquals(5, response.changes().actor().hpChange());
    }

    @Test
    @DisplayName("validateOnly=true면 resolve하지 않고 after가 before와 같다")
    void probeValidateOnlyShouldNotResolve() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "C001",
                actor(7, 5, 20, 20, Map.of("PAIN", 2)),
                target("ENEMY", "dummy_enemy", 30, 30, Map.of("SHIELD", 3)),
                selection("ENEMY", "dummy_enemy"),
                12345L,
                true
        ));

        assertTrue(response.valid());
        assertFalse(response.resolved());
        assertEquals(response.before().actor(), response.after().actor());
        assertEquals(response.before().targets(), response.after().targets());
        assertEquals(0, response.changes().actor().hpChange());
        assertEquals(0, response.changes().targets().get(0).hpChange());
    }

    @Test
    @DisplayName("없는 cardId는 명확한 NOT_FOUND 예외를 낸다")
    void probeShouldRejectUnknownCardId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.probe(new LabEffectProbeRequest(
                "NO_SUCH_CARD",
                actor(7, 5, 20, 20, Map.of()),
                target("ENEMY", "dummy_enemy", 30, 30, Map.of()),
                selection("ENEMY", "dummy_enemy"),
                12345L,
                false
        )));

        assertEquals(NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("card not found"));
    }

    @Test
    @DisplayName("지원하지 않는 target kind는 명확한 BAD_REQUEST 예외를 낸다")
    void probeShouldRejectUnsupportedTargetKind() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.probe(new LabEffectProbeRequest(
                "C001",
                actor(7, 5, 20, 20, Map.of()),
                target("SUMMON", "dummy_summon", 10, 10, Map.of()),
                null,
                12345L,
                false
        )));

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("unsupported target kind"));
    }

    @Test
    @DisplayName("validationErrors가 있으면 resolve가 실행되지 않는다")
    void probeShouldNotResolveWhenValidationFails() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "C001",
                actor(7, 5, 20, 20, Map.of()),
                target("ENEMY", "dummy_enemy", 30, 30, Map.of()),
                new LabProbeSelectionDto(List.of(), List.of(), List.of(), null),
                12345L,
                false
        ));

        assertFalse(response.valid());
        assertFalse(response.resolved());
        assertIterableEquals(List.of("exactly one target is required"), response.validationErrors());
        assertEquals(30, response.before().targets().get(0).hp());
        assertEquals(30, response.after().targets().get(0).hp());
    }

    @Test
    @DisplayName("Probe 가능한 카드 목록은 SKILL 카드만 반환한다")
    void cardsShouldReturnSkillCards() {
        assertFalse(service.cards().isEmpty());
        assertTrue(service.cards().stream().allMatch(card -> "SKILL".equals(card.type())));
        assertTrue(service.cards().stream().anyMatch(card -> "C001".equals(card.cardId())));
    }

    private LabProbeActorDto actor(int attackPower, int healPower, int hp, int maxHp, Map<String, Integer> statuses) {
        return new LabProbeActorDto(attackPower, healPower, hp, maxHp, statuses);
    }

    private LabProbeTargetDto target(String kind, String id, int hp, int maxHp, Map<String, Integer> statuses) {
        return new LabProbeTargetDto(kind, id, hp, maxHp, statuses);
    }

    private LabProbeSelectionDto selection(String kind, String id) {
        return new LabProbeSelectionDto(
                List.of(new LabProbeTargetDto(kind, id, null, null, Map.of())),
                List.of(),
                List.of(),
                null
        );
    }
}
