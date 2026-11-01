package com.hobozoo.aiqq.service.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.entity.rag.PipelineConfig;
import com.hobozoo.aiqq.entity.rag.BlockDraft;
import com.hobozoo.aiqq.entity.rag.ChildDraft;
import com.hobozoo.aiqq.entity.rag.ParentDraft;

/**
 * 父块按章节拼到目标长度，子块按字符滑窗。
 */
@Component
public class Chunker {

    private static final String SENTENCE_END = "。！？；!?;\n";

    public record Result(List<ParentDraft> parents, List<ChildDraft> children) {
    }

    private static final class Buffer {
        private final StringBuilder text = new StringBuilder();
        private final List<Integer> blockIndexes = new ArrayList<>();
        private int pageFrom;
        private int pageTo;
        private String[] section = new String[0];

        boolean isEmpty() {
            return text.isEmpty();
        }

        int length() {
            return text.length();
        }

        void append(BlockDraft block, int index) {
            if (text.isEmpty()) {
                pageFrom = block.pageNo();
                section = block.sectionPath();
            } else {
                text.append('\n');
            }
            text.append(block.text());
            blockIndexes.add(index);
            pageTo = block.pageNo();
        }

        void flushTo(List<ParentDraft> parents, boolean table) {
            if (text.isEmpty()) {
                return;
            }
            String value = text.toString();
            parents.add(new ParentDraft(value, pageFrom, pageTo, section, sha256(value),
                    List.copyOf(blockIndexes), table));
            text.setLength(0);
            blockIndexes.clear();
        }
    }

    public Result chunk(List<BlockDraft> blocks, PipelineConfig.Chunk config) {
        List<ParentDraft> parents = new ArrayList<>();
        Buffer buffer = new Buffer();

        for (int index = 0; index < blocks.size(); index++) {
            BlockDraft block = blocks.get(index);
            if ("table".equals(block.type())) {
                buffer.flushTo(parents, false);
                parents.add(new ParentDraft(block.text(), block.pageNo(), block.pageNo(), block.sectionPath(),
                        sha256(block.text()), List.of(index), true));
                continue;
            }

            String[] section = block.sectionPath();
            boolean sectionChanged = !buffer.isEmpty() && !sameSection(buffer.section, section);
            boolean full = buffer.length() >= config.parentTarget();
            boolean overflow = !buffer.isEmpty()
                    && buffer.length() + block.text().length() + 1 > config.parentMax();

            if (sectionChanged || full || overflow) {
                buffer.flushTo(parents, false);
            }

            if (block.text().length() > config.parentMax()) {
                for (String piece : splitLong(block.text(), config.parentTarget())) {
                    parents.add(new ParentDraft(piece, block.pageNo(), block.pageNo(), section,
                            sha256(piece), List.of(index), false));
                }
                continue;
            }
            buffer.append(block, index);
        }
        buffer.flushTo(parents, false);

        List<ChildDraft> children = new ArrayList<>();
        for (int parentIndex = 0; parentIndex < parents.size(); parentIndex++) {
            ParentDraft parent = parents.get(parentIndex);
            if (parent.table()) {
                children.add(new ChildDraft(parentIndex, parent.text(), parent.pageFrom(), parent.pageTo(),
                        parent.sha256(), children.size()));
                continue;
            }
            for (String window : windows(parent.text(), config.childSize(), config.childOverlap())) {
                children.add(new ChildDraft(parentIndex, window, parent.pageFrom(), parent.pageTo(),
                        sha256(window), children.size()));
            }
        }
        return new Result(parents, children);
    }

    private static boolean sameSection(String[] left, String[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!left[i].equals(right[i])) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitLong(String text, int target) {
        List<String> pieces = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            int end = Math.min(cursor + target, text.length());
            if (end < text.length()) {
                int boundary = lastBoundary(text, end, Math.max(cursor + target / 2, cursor));
                if (boundary > cursor) {
                    end = boundary;
                }
            }
            pieces.add(text.substring(cursor, end).strip());
            cursor = end;
        }
        return pieces;
    }

    private static List<String> windows(String text, int size, int overlap) {
        List<String> windows = new ArrayList<>();
        if (text.length() <= size) {
            windows.add(text);
            return windows;
        }
        int step = Math.max(size - overlap, 1);
        int cursor = 0;
        while (cursor < text.length()) {
            int end = Math.min(cursor + size, text.length());
            if (end < text.length()) {
                int boundary = lastBoundary(text, end, cursor + step);
                if (boundary > cursor) {
                    end = boundary;
                }
            }
            windows.add(text.substring(cursor, end).strip());
            if (end >= text.length()) {
                break;
            }
            cursor = Math.max(end - overlap, cursor + 1);
        }
        return windows;
    }

    private static int lastBoundary(String text, int end, int floor) {
        for (int i = end - 1; i >= floor && i >= 0; i--) {
            if (SENTENCE_END.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
