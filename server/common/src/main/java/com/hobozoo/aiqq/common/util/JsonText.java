package com.hobozoo.aiqq.common.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手写 jsonb 文本，只覆盖本项目需要的类型。
 */
public final class JsonText {

    private JsonText() {
    }

    public static String object(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
        }
        return builder.append('}').toString();
    }

    public static String array(Collection<?> values) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Object item : values) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(value(item));
        }
        return builder.append(']').toString();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(key), item));
            return object(converted);
        }
        if (value instanceof Collection<?> collection) {
            return array(collection);
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }
}
