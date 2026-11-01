package com.hobozoo.aiqq.service.rag;

/**
 * 流式文本里的 [n] 校验，越界编号直接丢弃。
 */
public final class CitationFilter {

    private static final int MAX_MARKER = 8;

    private final int maxIndex;
    private final StringBuilder pending = new StringBuilder();
    private int invalid;

    public CitationFilter(int maxIndex) {
        this.maxIndex = maxIndex;
    }

    public String feed(String text) {
        pending.append(text);
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < pending.length()) {
            char c = pending.charAt(index);
            if (c != '[') {
                out.append(c);
                index++;
                continue;
            }
            int close = pending.indexOf("]", index);
            if (close < 0) {
                if (pending.length() - index > MAX_MARKER) {
                    out.append(c);
                    index++;
                    continue;
                }
                break;
            }
            String inner = pending.substring(index + 1, close);
            if (inner.matches("\\d{1,3}")) {
                int number = Integer.parseInt(inner);
                if (number >= 1 && number <= maxIndex) {
                    out.append(pending, index, close + 1);
                } else {
                    invalid++;
                }
                index = close + 1;
                continue;
            }
            out.append(c);
            index++;
        }
        pending.delete(0, index);
        return out.toString();
    }

    public String flush() {
        String rest = pending.toString();
        pending.setLength(0);
        return rest;
    }

    public int invalid() {
        return invalid;
    }
}
