package com.example.dueltower.lab.service;

import com.example.dueltower.lab.dto.LabDiceRequest;
import com.example.dueltower.lab.dto.LabDiceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class LabDiceServiceTest {

    private final LabDiceService service = new LabDiceService();

    @Test
    @DisplayName("3d6+2 요청은 spec, min, max, expected를 반환한다")
    void calculateShouldReturnSummaryForThreeD6PlusTwo() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("3d6+2", 0, null));

        assertEquals("3d6+2", response.notation());
        assertEquals(3, response.spec().count());
        assertEquals(6, response.spec().sides());
        assertEquals(2, response.spec().modifier());
        assertEquals(5, response.min());
        assertEquals(20, response.max());
        assertEquals("12.5", response.expected());
        assertEquals(25L, response.expectedNumerator());
        assertEquals(2L, response.expectedDenominator());
        assertTrue(response.expectedAvailable());
        assertEquals("3d6+2", response.normalizedNotation());
        assertEquals(2, response.expression().terms().size());
    }

    @Test
    @DisplayName("d20 요청은 count 생략을 1로 해석한다")
    void calculateShouldDefaultOmittedDiceCountToOne() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("d20", 0, null));

        assertEquals(1, response.spec().count());
        assertEquals(20, response.spec().sides());
        assertEquals(0, response.spec().modifier());
        assertEquals(1, response.min());
        assertEquals(20, response.max());
    }

    @Test
    @DisplayName("1D6+2D4+3 요청은 복합식 min/max를 반환한다")
    void calculateShouldReturnMinMaxForCompositeExpression() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("1D6+2D4+3", 0, null));

        assertEquals("1D6+2D4+3", response.normalizedNotation());
        assertEquals(6, response.min());
        assertEquals(17, response.max());
        assertEquals("11.5", response.expected());
        assertTrue(response.expectedAvailable());
        assertEquals(23L, response.expectedNumerator());
        assertEquals(2L, response.expectedDenominator());
        assertEquals(3, response.expression().terms().size());
    }

    @Test
    @DisplayName("selector 주사위식은 min/max를 반환한다")
    void calculateShouldReturnMinMaxForSelectorExpressions() {
        LabDiceResponse keepHigh = service.calculate(new LabDiceRequest("4D6KH2", 0, null));
        assertEquals(2, keepHigh.min());
        assertEquals(12, keepHigh.max());

        LabDiceResponse max = service.calculate(new LabDiceRequest("4D6MAX", 0, null));
        assertEquals(1, max.min());
        assertEquals(6, max.max());
    }

    @Test
    @DisplayName("복합 selector 주사위식은 expression terms를 반환한다")
    void calculateShouldReturnExpressionForMixedSelectorExpression() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("3D6KH2+2D4MAX+1", 5, 123L));

        assertEquals(3, response.expression().terms().size());
        assertEquals(5, response.rolls().size());
        assertEquals("N/A", response.expected());
        assertTrue(!response.expectedAvailable());
        assertTrue(response.expectedNote().contains("expectedDice"));
    }

    @Test
    @DisplayName("selector 기대값이 미지원이어도 rolls는 정상 반환한다")
    void calculateShouldReturnRollsWhenSelectorExpectedIsUnsupported() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("4D6KH2", 20, 9876L));

        assertEquals("N/A", response.expected());
        assertTrue(!response.expectedAvailable());
        assertEquals(20, response.rolls().size());
        assertTrue(response.rolls().stream().allMatch(roll -> roll >= 2 && roll <= 12));
    }

    @Test
    @DisplayName("seed가 같으면 rolls가 재현된다")
    void calculateShouldReturnDeterministicRollsForSeed() {
        LabDiceRequest request = new LabDiceRequest("3d6+2", 20, 12345L);

        LabDiceResponse first = service.calculate(request);
        LabDiceResponse second = service.calculate(request);

        assertIterableEquals(first.rolls(), second.rolls());
        assertEquals(20, first.rollCount());
        assertEquals(20, first.rolls().size());
        assertEquals(12345L, first.seed());
    }

    @Test
    @DisplayName("seed가 같으면 복합 주사위식 rolls가 재현된다")
    void calculateShouldReturnDeterministicRollsForCompositeExpressionSeed() {
        LabDiceRequest request = new LabDiceRequest("3D6KH2+2D4MAX+1", 20, 12345L);

        LabDiceResponse first = service.calculate(request);
        LabDiceResponse second = service.calculate(request);

        assertIterableEquals(first.rolls(), second.rolls());
        assertEquals(20, first.rolls().size());
    }

    @Test
    @DisplayName("rollCount=0이면 roll 없이 요약과 빈 히스토그램을 반환한다")
    void calculateShouldSkipRollsWhenRollCountIsZero() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("3d6+2", 0, 12345L));

        assertEquals(0, response.rollCount());
        assertTrue(response.rolls().isEmpty());
        assertTrue(response.histogram().isEmpty());
        assertEquals(5, response.min());
        assertEquals(20, response.max());
        assertEquals("12.5", response.expected());
    }

    @Test
    @DisplayName("roll 결과는 값 오름차순 히스토그램으로 집계된다")
    void calculateShouldReturnSortedHistogram() {
        LabDiceResponse response = service.calculate(new LabDiceRequest("d1", 3, 7L));

        assertIterableEquals(List.of(1, 1, 1), response.rolls());
        assertEquals(1, response.histogram().size());
        assertEquals(1, response.histogram().get(0).value());
        assertEquals(3, response.histogram().get(0).count());
    }

    @Test
    @DisplayName("invalid notation이면 BAD_REQUEST 예외가 발생한다")
    void calculateShouldRejectInvalidNotation() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.calculate(new LabDiceRequest("2x6", 1, null))
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("invalid dice notation"));
    }

    @Test
    @DisplayName("notation은 필수다")
    void calculateShouldRequireNotation() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.calculate(new LabDiceRequest(null, 1, null))
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("notation"));
    }

    @Test
    @DisplayName("rollCount=1001이면 BAD_REQUEST 예외가 발생한다")
    void calculateShouldRejectTooLargeRollCount() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.calculate(new LabDiceRequest("d20", 1001, null))
        );

        assertEquals(BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("rollCount"));
    }
}
