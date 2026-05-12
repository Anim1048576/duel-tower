package com.example.dueltower.lab.service;

import com.example.dueltower.lab.dto.LabEffectProbeRequest;
import com.example.dueltower.lab.dto.LabEffectProbeResponse;
import com.example.dueltower.lab.dto.LabProbeActorDto;
import com.example.dueltower.lab.dto.LabProbeExtraCardDto;
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

    @Test
    @DisplayName("targets 배열로 여러 ENEMY를 만들고 enemy all 카드가 모든 대상에 적용된다")
    void probeShouldSupportMultipleEnemyTargets() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "Tig005_Card",
                actor(7, 5, 20, 20, Map.of("Tig201_Status", 3)),
                null,
                List.of(
                        target("ENEMY", "enemy_a", 30, 30, Map.of()),
                        target("ENEMY", "enemy_b", 25, 25, Map.of())
                ),
                new LabProbeSelectionDto(
                        null,
                        List.of(),
                        List.of(),
                        List.of("hand_1"),
                        List.of(),
                        null
                ),
                List.of(new LabProbeExtraCardDto("hand_1", "C001", "HAND")),
                12345L,
                false
        ));

        assertTrue(response.valid());
        assertTrue(response.resolved());
        assertEquals(2, response.before().targets().size());
        assertEquals(-10, response.changes().targets().get(0).hpChange());
        assertEquals(-10, response.changes().targets().get(1).hpChange());
        assertTrue(response.notes().stream().anyMatch(note -> note.contains("Target states created: 2")));
        assertTrue(response.notes().stream().anyMatch(note -> note.contains("hand_1")));
    }

    @Test
    @DisplayName("PLAYER 대상은 TargetRef.ofPlayer로 변환되어 ally one 회복 카드 실험에 사용할 수 있다")
    void probeShouldSupportPlayerTarget() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "C002",
                actor(2, 5, 20, 20, Map.of()),
                target("PLAYER", "ally_player", 7, 20, Map.of()),
                selection("PLAYER", "ally_player"),
                12345L,
                false
        ));

        assertTrue(response.valid());
        assertTrue(response.resolved());
        assertEquals("PLAYER", response.before().targets().get(0).kind());
        assertEquals(7, response.before().targets().get(0).hp());
        assertEquals(12, response.after().targets().get(0).hp());
        assertEquals(5, response.changes().targets().get(0).hpChange());
    }

    @Test
    @DisplayName("SELF 대상 카드는 target 없이 actor만으로 validate/resolve 된다")
    void probeShouldSupportSelfWithoutTarget() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "C003",
                actor(2, 5, 20, 20, Map.of()),
                null,
                new LabProbeSelectionDto(List.of(), List.of(), List.of(), null),
                12345L,
                false
        ));

        assertTrue(response.valid());
        assertTrue(response.resolved());
        assertTrue(response.before().targets().isEmpty());
        assertEquals(2, response.after().actor().statuses().get("SHIELD"));
        assertEquals("SHIELD", response.changes().actor().statusChanges().get(0).statusId());
    }

    @Test
    @DisplayName("extraCards alias는 selectedIds 입력으로 변환되어 카드 선택 요구 효과를 실험할 수 있다")
    void probeShouldMapSelectedAliasesToExtraCards() {
        LabEffectProbeResponse response = service.probe(new LabEffectProbeRequest(
                "Tig001_Card",
                actor(7, 5, 20, 20, Map.of("Tig201_Status", 3)),
                target("ENEMY", "dummy_enemy", 30, 30, Map.of()),
                null,
                new LabProbeSelectionDto(
                        List.of(new LabProbeTargetDto("ENEMY", "dummy_enemy", null, null, Map.of())),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("installed_1"),
                        null
                ),
                List.of(new LabProbeExtraCardDto("installed_1", "Tig008_Card", "FIELD")),
                12345L,
                false
        ));

        assertTrue(response.valid());
        assertTrue(response.resolved());
        assertTrue(response.events().stream().anyMatch(event -> "CardsMoved".equals(event.type())));
        assertTrue(response.notes().stream().anyMatch(note -> note.contains("installed_1")));
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
