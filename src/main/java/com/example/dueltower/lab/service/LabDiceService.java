package com.example.dueltower.lab.service;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.common.util.Rational;
import com.example.dueltower.lab.dto.LabDiceHistogramEntryDto;
import com.example.dueltower.lab.dto.LabDiceRequest;
import com.example.dueltower.lab.dto.LabDiceResponse;
import com.example.dueltower.lab.dto.LabDiceSpecDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LabDiceService {

    private static final int DEFAULT_ROLL_COUNT = 1;
    private static final int MAX_ROLL_COUNT = 1000;

    public LabDiceResponse calculate(LabDiceRequest request) {
        if (request == null) {
            throw badRequest("request body is required");
        }

        String notation = request.notation();
        DiceUtility.DiceSpec spec = parseSpec(notation);
        String normalizedNotation = notation.trim();
        int rollCount = normalizeRollCount(request.rollCount());

        Rational expected = expectedDice(normalizedNotation);
        List<Integer> rolls = roll(normalizedNotation, rollCount, request.seed());
        List<LabDiceHistogramEntryDto> histogram = histogram(rolls);

        return new LabDiceResponse(
                normalizedNotation,
                new LabDiceSpecDto(spec.count(), spec.sides(), spec.modifier()),
                minDice(normalizedNotation),
                maxDice(normalizedNotation),
                formatExpected(expected),
                expected.getNumerator(),
                expected.getDenominator(),
                rollCount,
                request.seed(),
                rolls,
                histogram
        );
    }

    private DiceUtility.DiceSpec parseSpec(String notation) {
        try {
            return DiceUtility.parseDice(notation);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private Rational expectedDice(String notation) {
        try {
            return DiceUtility.expectedDice(notation);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private int minDice(String notation) {
        try {
            return DiceUtility.minDice(notation);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private int maxDice(String notation) {
        try {
            return DiceUtility.maxDice(notation);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private int normalizeRollCount(Integer rollCount) {
        int normalized = rollCount == null ? DEFAULT_ROLL_COUNT : rollCount;
        if (normalized < 0 || normalized > MAX_ROLL_COUNT) {
            throw badRequest("rollCount must be between 0 and " + MAX_ROLL_COUNT);
        }
        return normalized;
    }

    private List<Integer> roll(String notation, int rollCount, Long seed) {
        if (rollCount == 0) {
            return List.of();
        }

        Random rng = seed == null ? ThreadLocalRandom.current() : new Random(seed);
        List<Integer> rolls = new ArrayList<>(rollCount);
        for (int i = 0; i < rollCount; i++) {
            try {
                rolls.add(DiceUtility.rollDice(notation, rng));
            } catch (IllegalArgumentException | ArithmeticException ex) {
                throw badRequest(ex.getMessage());
            }
        }
        return List.copyOf(rolls);
    }

    private List<LabDiceHistogramEntryDto> histogram(List<Integer> rolls) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (Integer roll : rolls) {
            counts.merge(roll, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new LabDiceHistogramEntryDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String formatExpected(Rational value) {
        if (value.getDenominator() == 1L) {
            return Long.toString(value.getNumerator());
        }
        return BigDecimal.valueOf(value.getNumerator())
                .divide(BigDecimal.valueOf(value.getDenominator()), MathContext.DECIMAL64)
                .stripTrailingZeros()
                .toPlainString();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
