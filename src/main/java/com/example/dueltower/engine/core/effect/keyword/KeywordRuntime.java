package com.example.dueltower.engine.core.effect.keyword;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed keyword token.
 * Examples:
 * - "부동" -> id="부동", param=null
 * - "치명(2)" -> id="치명", param=2
 */
public record KeywordRuntime(String id, Integer param, String raw) {

    private static final Pattern PARAM_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s*\\(\\s*(-?\\d+)\\s*\\)\\s*$");

    public static KeywordRuntime parse(String raw) {
        if (raw == null) return new KeywordRuntime("", null, "");
        String t = raw.trim();
        if (t.isEmpty()) return new KeywordRuntime("", null, "");

        Matcher m = PARAM_PATTERN.matcher(t);
        if (m.matches()) {
            String id = m.group(1).trim();
            Integer param = Integer.parseInt(m.group(2));
            return new KeywordRuntime(id, param, t);
        }
        return new KeywordRuntime(t, null, t);
    }
}
