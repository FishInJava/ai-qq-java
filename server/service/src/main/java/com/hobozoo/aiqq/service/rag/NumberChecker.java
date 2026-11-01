package com.hobozoo.aiqq.service.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 答案数字与原文核对。
 */
public final class NumberChecker {

    public record Result(int total, List<String> missing) {
    }

    private record Token(String raw, double value) {
    }

    private static final double TOLERANCE = 1e-6;
    private static final String CN = "零〇一二两三四五六七八九十百千万亿";
    private static final String UNITS = "万亿元|万亿|亿元|万元|亿美元|万美元|美元|亿|万|元|倍|%";
    private static final Pattern TOKEN = Pattern.compile(
            "百分之([" + CN + "]+)"
                    + "|([" + CN + "]+)\\s*(" + UNITS + ")"
                    + "|([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(" + UNITS + ")"
                    + "|([0-9][0-9,]*(?:\\.[0-9]+)?)");

    private NumberChecker() {
    }

    public static Result check(String answer, List<String> sources) {
        List<Token> wanted = extract(answer);
        List<Token> known = new ArrayList<>();
        for (String source : sources) {
            known.addAll(extract(source));
        }
        List<String> missing = new ArrayList<>();
        for (Token token : wanted) {
            if (!contains(known, token)) {
                missing.add(token.raw());
            }
        }
        return new Result(wanted.size(), missing);
    }

    static List<Token> extract(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String normalized = halfWidth(text);
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                tokens.add(new Token(matcher.group(), chinese(matcher.group(1)) / 100.0));
            } else if (matcher.group(2) != null) {
                tokens.add(new Token(matcher.group(), scale(chinese(matcher.group(2)), matcher.group(3))));
            } else if (matcher.group(4) != null) {
                if (!excluded(normalized, matcher.start(), matcher.end())) {
                    tokens.add(new Token(matcher.group(), scale(number(matcher.group(4)), matcher.group(5))));
                }
            } else if (!excluded(normalized, matcher.start(), matcher.end())) {
                tokens.add(new Token(matcher.group(), number(matcher.group(6))));
            }
        }
        return tokens;
    }

    private static boolean contains(List<Token> known, Token wanted) {
        for (Token token : known) {
            if (Math.abs(token.value() - wanted.value()) <= TOLERANCE * Math.max(1, Math.abs(wanted.value()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean excluded(String text, int start, int end) {
        if (start > 0 && text.charAt(start - 1) == '[' && end < text.length() && text.charAt(end) == ']') {
            return true;
        }
        int before = start - 1;
        while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
            before--;
        }
        if (before >= 0 && text.charAt(before) == '第') {
            return true;
        }
        int after = end;
        while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
            after++;
        }
        return after < text.length() && text.charAt(after) == '页';
    }

    private static double scale(double value, String unit) {
        return switch (unit) {
            case "%" -> value / 100;
            case "万亿元", "万亿" -> value * 1_000_000_000_000.0;
            case "亿元", "亿" -> value * 100_000_000;
            case "万元" -> value * 10_000;
            case "万" -> value * 10_000;
            default -> value;
        };
    }

    private static double number(String text) {
        return Double.parseDouble(text.replace(",", ""));
    }

    private static String halfWidth(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '０' && c <= '９') {
                builder.append((char) (c - '０' + '0'));
            } else if (c == '％') {
                builder.append('%');
            } else if (c == '．') {
                builder.append('.');
            } else if (c == '，') {
                builder.append(',');
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    static double chinese(String text) {
        double total = 0;
        double section = 0;
        double current = 0;
        for (char c : text.toCharArray()) {
            int digit = digit(c);
            if (digit >= 0) {
                current = digit;
            } else if (c == '十' || c == '百' || c == '千') {
                int base = c == '十' ? 10 : c == '百' ? 100 : 1000;
                section += (current == 0 ? 1 : current) * base;
                current = 0;
            } else if (c == '万' || c == '亿') {
                section += current;
                total += (section == 0 ? 1 : section) * (c == '万' ? 10_000 : 100_000_000);
                section = 0;
                current = 0;
            }
        }
        return total + section + current;
    }

    private static int digit(char c) {
        return switch (c) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }
}
