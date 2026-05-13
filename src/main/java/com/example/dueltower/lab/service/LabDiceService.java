package com.example.dueltower.lab.service;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.common.util.Rational;
import com.example.dueltower.lab.dto.LabDiceExpressionDto;
import com.example.dueltower.lab.dto.LabDiceHistogramEntryDto;
import com.example.dueltower.lab.dto.LabDiceRequest;
import com.example.dueltower.lab.dto.LabDiceResponse;
import com.example.dueltower.lab.dto.LabDiceSpecDto;
import com.example.dueltower.lab.dto.LabDiceTermDto;
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
        DiceUtility.DiceExpression expression = parseExpression(notation);
        LabDiceSpecDto spec = parseLegacySpec(notation);
        String normalizedNotation = normalizeNotation(notation);
        int rollCount = normalizeRollCount(request.rollCount());

        ExpectedResult expected = expectedDice(normalizedNotation);
        List<Integer> rolls = roll(normalizedNotation, rollCount, request.seed());
        List<LabDiceHistogramEntryDto> histogram = histogram(rolls);

        return new LabDiceResponse(
                notation,
                normalizedNotation,
                spec,
                toExpressionDto(expression),
                minDice(normalizedNotation),
                maxDice(normalizedNotation),
                expected.available(),
                expected.display(),
                expected.numerator(),
                expected.denominator(),
                expected.note(),
                rollCount,
                request.seed(),
                rolls,
                histogram
        );
    }

    private DiceUtility.DiceExpression parseExpression(String notation) {
        try {
            return DiceUtility.parseDiceExpression(notation);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private LabDiceSpecDto parseLegacySpec(String notation) {
        try {
            DiceUtility.DiceSpec spec = DiceUtility.parseDice(notation);
            return new LabDiceSpecDto(spec.count(), spec.sides(), spec.modifier());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ExpectedResult expectedDice(String notation) {
        try {
            Rational value = DiceUtility.expectedDice(notation);
            return new ExpectedResult(
                    true,
                    formatExpected(value),
                    value.getNumerator(),
                    value.getDenominator(),
                    null
            );
        } catch (UnsupportedOperationException ex) {
            return new ExpectedResult(false, "N/A", null, null, ex.getMessage());
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

    private String normalizeNotation(String notation) {
        return notation.replaceAll("\\s+", "");
    }

    private LabDiceExpressionDto toExpressionDto(DiceUtility.DiceExpression expression) {
        return new LabDiceExpressionDto(expression.terms().stream()
                .map(this::toTermDto)
                .toList());
    }

    private LabDiceTermDto toTermDto(DiceUtility.SignedTerm signedTerm) {
        DiceUtility.DiceTerm term = signedTerm.term();
        if (term instanceof DiceUtility.ConstantTerm constant) {
            return new LabDiceTermDto(
                    signedTerm.sign(),
                    "CONSTANT",
                    constant.value(),
                    null,
                    null,
                    null,
                    null,
                    displaySign(signedTerm.sign()) + constant.value()
            );
        }
        if (term instanceof DiceUtility.DiceRollTerm dice) {
            String selector = dice.selector() == null ? null : dice.selector().kind().name();
            Integer selectorAmount = dice.selector() == null ? null : dice.selector().amount();
            return new LabDiceTermDto(
                    signedTerm.sign(),
                    "DICE",
                    null,
                    dice.count(),
                    dice.sides(),
                    selector,
                    selectorAmount,
                    displayDiceTerm(signedTerm.sign(), dice)
            );
        }
        throw new IllegalArgumentException("unsupported dice term: " + term);
    }

    private String displayDiceTerm(int sign, DiceUtility.DiceRollTerm dice) {
        StringBuilder display = new StringBuilder();
        display.append(displaySign(sign));
        display.append(dice.count()).append("D").append(dice.sides());
        if (dice.selector() != null) {
            display.append(dice.selector().kind()).append(dice.selector().amount());
        }
        return display.toString();
    }

    private String displaySign(int sign) {
        return sign < 0 ? "-" : "+";
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

    private record ExpectedResult(
            boolean available,
            String display,
            Long numerator,
            Long denominator,
            String note
    ) {
    }
}
