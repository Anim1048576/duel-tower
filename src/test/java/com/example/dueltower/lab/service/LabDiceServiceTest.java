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
        assertEquals(25, response.expectedNumerator());
        assertEquals(2, response.expectedDenominator());
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
