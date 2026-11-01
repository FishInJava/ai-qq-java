package com.hobozoo.aiqq.service.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.entity.rag.BlockDraft;
import com.hobozoo.aiqq.entity.rag.PipelineConfig;

/**
 * 去页眉页脚与目录项、认标题、拼段落、找表格。
 */
@Component
public class BlockAssembler {

    private static final Pattern PURE_NUMBER = Pattern.compile("^[\\d\\s.\\-—/()（）]+$");
    private static final Pattern HEADING_NUMBER = Pattern.compile("^\\d+(\\.\\d+)*[\\s、.]");
    private static final Pattern TOC_LINE = Pattern.compile("\\.{3,}\\s*\\d+\\s*$|…{2,}");
    private static final Pattern TOC_TITLE = Pattern.compile("^.{0,8}(内容|图表|目\\s*次)?目\\s*录$");
    private static final Pattern DATE = Pattern.compile("\\d{4}\\s*年\\s*\\d{1,2}\\s*月");

    public record Assembled(List<BlockDraft> blocks, String structureSource, String title,
                            Map<String, Integer> removed) {
    }

    private record SectionEntry(int level, String title) {
    }

    public Assembled assemble(List<PdfExtractor.Page> pages, PipelineConfig config) {
        double bodySize = bodyFontSize(pages);
        Set<Integer> tocPages = tocPages(pages);
        Set<String> runningKeys = repeatedKeys(pages);
        Map<String, Integer> removed = new LinkedHashMap<>();
        Map<Integer, List<PdfExtractor.Line>> pageLines = withoutHeaders(pages, config.header(), removed);
        for (List<PdfExtractor.Line> lines : pageLines.values()) {
            lines.removeIf(line -> {
                if (tocEntry(line)) {
                    removed.merge("toc", 1, Integer::sum);
                    return true;
                }
                return false;
            });
        }

        List<Double> headingSizes = new ArrayList<>();
        for (PdfExtractor.Page page : pages) {
            if (tocPages.contains(page.pageNo())) {
                continue;
            }
            List<PdfExtractor.Line> lines = pageLines.getOrDefault(page.pageNo(), List.of());
            for (int index = 0; index < lines.size(); index++) {
                if (isHeading(lines, index, bodySize, config.heading()) && !dateOnly(lines.get(index))) {
                    headingSizes.add(round(lines.get(index).fontSize()));
                }
            }
        }
        List<Double> levels = headingSizes.stream().distinct().sorted((a, b) -> Double.compare(b, a)).toList();

        List<SectionEntry> stack = new ArrayList<>();
        List<BlockDraft> blocks = new ArrayList<>();
        String title = pickTitle(pageLines.getOrDefault(1, List.of()), runningKeys);

        for (PdfExtractor.Page page : pages) {
            List<PdfExtractor.Line> lines = pageLines.getOrDefault(page.pageNo(), List.of());
            boolean[] table = tableMask(lines);
            boolean headingAllowed = !tocPages.contains(page.pageNo());
            int order = 0;

            int index = 0;
            while (index < lines.size()) {
                PdfExtractor.Line line = lines.get(index);
                if (table[index]) {
                    int end = index;
                    while (end + 1 < lines.size() && table[end + 1]) {
                        end++;
                    }
                    blocks.add(tableBlock(page.pageNo(), lines.subList(index, end + 1), order++, stack));
                    index = end + 1;
                    continue;
                }
                if (headingAllowed && !inZone((line.y0() + line.y1()) / 2, page, config.header())
                        && isHeading(lines, index, bodySize, config.heading())
                        && !dateOnly(line) && !runningKeys.contains(normalize(line.text()))) {
                    int level = levelOf(line, levels, config.heading().maxLevel());
                    while (!stack.isEmpty() && stack.get(stack.size() - 1).level() >= level) {
                        stack.remove(stack.size() - 1);
                    }
                    stack.add(new SectionEntry(level, line.text().trim()));
                    blocks.add(new BlockDraft(page.pageNo(), "heading", line.x0(), line.y0(), line.x1(), line.y1(),
                            line.text().trim(), order++, sectionPath(stack), round(line.fontSize()), line.fontName()));
                    index++;
                    continue;
                }

                List<PdfExtractor.Line> paragraph = new ArrayList<>();
                paragraph.add(line);
                int next = index + 1;
                while (next < lines.size()) {
                    PdfExtractor.Line candidate = lines.get(next);
                    if (table[next] || (headingAllowed && !inZone((lines.get(next).y0() + lines.get(next).y1()) / 2, page, config.header())
                            && isHeading(lines, next, bodySize, config.heading())
                            && !dateOnly(lines.get(next)) && !runningKeys.contains(normalize(lines.get(next).text())))) {
                        break;
                    }
                    double gap = paragraph.get(paragraph.size() - 1).y0() - candidate.y1();
                    if (gap > candidate.fontSize() * 1.8) {
                        break;
                    }
                    paragraph.add(candidate);
                    next++;
                }
                blocks.add(paragraphBlock(page.pageNo(), paragraph, order++, stack));
                index = next;
            }
        }

        String structureSource = headingSizes.isEmpty() ? "flat" : "font_guess";
        return new Assembled(blocks, structureSource, title, removed);
    }

    private static BlockDraft paragraphBlock(int pageNo, List<PdfExtractor.Line> lines, int order,
                                             List<SectionEntry> stack) {
        double x0 = lines.stream().mapToDouble(PdfExtractor.Line::x0).min().orElse(0);
        double x1 = lines.stream().mapToDouble(PdfExtractor.Line::x1).max().orElse(0);
        double y0 = lines.stream().mapToDouble(PdfExtractor.Line::y0).min().orElse(0);
        double y1 = lines.stream().mapToDouble(PdfExtractor.Line::y1).max().orElse(0);
        double size = lines.stream().mapToDouble(PdfExtractor.Line::fontSize).max().orElse(0);
        String font = lines.isEmpty() ? null : lines.get(0).fontName();
        String text = join(lines);
        return new BlockDraft(pageNo, "text", x0, y0, x1, y1, text, order, sectionPath(stack), round(size), font);
    }

    private static BlockDraft tableBlock(int pageNo, List<PdfExtractor.Line> lines, int order,
                                         List<SectionEntry> stack) {
        double x0 = lines.stream().mapToDouble(PdfExtractor.Line::x0).min().orElse(0);
        double x1 = lines.stream().mapToDouble(PdfExtractor.Line::x1).max().orElse(0);
        double y0 = lines.stream().mapToDouble(PdfExtractor.Line::y0).min().orElse(0);
        double y1 = lines.stream().mapToDouble(PdfExtractor.Line::y1).max().orElse(0);
        StringBuilder text = new StringBuilder();
        for (PdfExtractor.Line line : lines) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            List<String> cells = line.cells().stream()
                    .map(PdfExtractor.Cell::text)
                    .filter(cell -> !cell.isBlank())
                    .toList();
            text.append(String.join(" | ", cells));
        }
        return new BlockDraft(pageNo, "table", x0, y0, x1, y1, text.toString(), order, sectionPath(stack),
                null, null);
    }

    private static String join(List<PdfExtractor.Line> lines) {
        StringBuilder text = new StringBuilder();
        for (PdfExtractor.Line line : lines) {
            String piece = line.text().strip();
            if (piece.isEmpty()) {
                continue;
            }
            if (!text.isEmpty()) {
                char previous = text.charAt(text.length() - 1);
                char next = piece.charAt(0);
                if (!(isCjk(previous) && isCjk(next))) {
                    text.append(' ');
                }
            }
            text.append(piece);
        }
        return text.toString();
    }

    private Set<Integer> tocPages(List<PdfExtractor.Page> pages) {
        Set<Integer> result = new HashSet<>();
        for (PdfExtractor.Page page : pages) {
            if (page.pageNo() > 6) {
                continue;
            }
            boolean title = page.lines().stream()
                    .anyMatch(line -> TOC_TITLE.matcher(line.text().replaceAll("\\s+", "")).matches());
            long dotted = page.lines().stream().filter(BlockAssembler::tocEntry).count();
            if (title || dotted >= 3) {
                result.add(page.pageNo());
            }
        }
        return result;
    }

    private static boolean tocEntry(PdfExtractor.Line line) {
        return TOC_LINE.matcher(line.text()).find();
    }

    private Map<Integer, List<PdfExtractor.Line>> withoutHeaders(List<PdfExtractor.Page> pages,
                                                                 PipelineConfig.Header header,
                                                                 Map<String, Integer> removed) {
        Map<String, List<Integer>> occurrences = new HashMap<>();
        for (PdfExtractor.Page page : pages) {
            for (PdfExtractor.Line line : page.lines()) {
                double center = (line.y0() + line.y1()) / 2;
                if (!nearEdge(center, page)) {
                    continue;
                }
                occurrences.computeIfAbsent(normalize(line.text()), key -> new ArrayList<>()).add(page.pageNo());
            }
        }

        Map<Integer, List<PdfExtractor.Line>> result = new LinkedHashMap<>();
        for (PdfExtractor.Page page : pages) {
            List<PdfExtractor.Line> kept = new ArrayList<>();
            for (PdfExtractor.Line line : page.lines()) {
                double center = (line.y0() + line.y1()) / 2;
                boolean inZone = inZone(center, page, header);
                String key = normalize(line.text());
                List<Integer> pagesSeen = occurrences.getOrDefault(key, List.of());
                boolean runningHead = nearEdge(center, page) && pagesSeen.size() >= header.minRepeatPages()
                        && line.text().strip().length() < 60;
                boolean pageNumber = inZone && PURE_NUMBER.matcher(line.text().strip()).matches();
                if (runningHead || pageNumber) {
                    removed.merge(runningHead ? "running_head" : "page_number", 1, Integer::sum);
                    continue;
                }
                kept.add(line);
            }
            result.put(page.pageNo(), kept);
        }
        return result;
    }

    private static String pickTitle(List<PdfExtractor.Line> lines, Set<String> runningKeys) {
        PdfExtractor.Line best = null;
        for (PdfExtractor.Line line : lines) {
            String text = line.text().strip();
            if (text.length() < 4 || text.length() > 60) {
                continue;
            }
            if (DATE.matcher(text).find() || PURE_NUMBER.matcher(text).matches()) {
                continue;
            }
            if (runningKeys.contains(normalize(text))) {
                continue;
            }
            if (text.endsWith("。") || text.endsWith("，") || text.endsWith("；")) {
                continue;
            }
            if (best == null || line.fontSize() > best.fontSize()) {
                best = line;
            }
        }
        return best == null ? null : stripDate(best.text());
    }

    private static Set<String> repeatedKeys(List<PdfExtractor.Page> pages) {
        Map<String, Set<Integer>> seen = new HashMap<>();
        for (PdfExtractor.Page page : pages) {
            for (PdfExtractor.Line line : page.lines()) {
                if (line.text().strip().length() < 60) {
                    seen.computeIfAbsent(normalize(line.text()), key -> new HashSet<>()).add(page.pageNo());
                }
            }
        }
        Set<String> keys = new HashSet<>();
        seen.forEach((key, pageNos) -> {
            if (pageNos.size() >= 3) {
                keys.add(key);
            }
        });
        return keys;
    }

    private static boolean dateOnly(PdfExtractor.Line line) {
        String text = line.text().strip();
        return text.length() <= 25 && DATE.matcher(text).find();
    }

    private static boolean nearEdge(double center, PdfExtractor.Page page) {
        return center < page.height() * 0.25 || center > page.height() * 0.75;
    }

    private static boolean inZone(double center, PdfExtractor.Page page, PipelineConfig.Header header) {
        return center < page.height() * header.zoneRatio()
                || center > page.height() * (1 - header.zoneRatio());
    }

    private static boolean[] tableMask(List<PdfExtractor.Line> lines) {
        boolean[] mask = new boolean[lines.size()];
        int index = 0;
        while (index < lines.size()) {
            if (!candidateRow(lines.get(index))) {
                index++;
                continue;
            }
            int end = index;
            while (end + 1 < lines.size() && candidateRow(lines.get(end + 1))) {
                end++;
            }
            int rows = end - index + 1;
            int digits = 0;
            for (int i = index; i <= end; i++) {
                digits += lines.get(i).digits();
            }
            if (rows >= 2 && digits >= 3) {
                Arrays.fill(mask, index, end + 1, true);
            }
            index = end + 1;
        }
        return mask;
    }

    private static boolean candidateRow(PdfExtractor.Line line) {
        long cells = line.cells().stream().filter(cell -> !cell.text().isBlank()).count();
        return cells >= 2 && line.text().length() <= 300;
    }

    private static boolean isHeading(List<PdfExtractor.Line> lines, int index, double bodySize,
                                     PipelineConfig.Heading heading) {
        PdfExtractor.Line line = lines.get(index);
        String text = line.text().strip();
        if (text.isEmpty() || text.length() > 45) {
            return false;
        }
        if (text.endsWith("。") || text.endsWith("，") || text.endsWith("；") || text.endsWith("、")) {
            return false;
        }
        boolean numbered = HEADING_NUMBER.matcher(text).find() && line.fontSize() >= bodySize
                && text.length() <= 40 && text.indexOf('，') < 0 && text.indexOf('。') < 0;
        if (numbered) {
            return true;
        }
        if (text.length() > 30) {
            return false;
        }
        boolean large = line.fontSize() >= bodySize * heading.sizeRatio();
        boolean boldAndLarger = line.bold() && line.fontSize() >= bodySize * heading.boldSizeRatio();
        if (!large && !boldAndLarger) {
            return false;
        }
        double gapAbove = index > 0 ? lines.get(index - 1).y0() - line.y1() : 999;
        double gapBelow = index + 1 < lines.size() ? line.y0() - lines.get(index + 1).y1() : 999;
        return gapAbove >= line.fontSize() * heading.lineGapRatio()
                || gapBelow >= line.fontSize() * heading.lineGapRatio();
    }

    private static String stripDate(String text) {
        String stripped = text.strip();
        Matcher matcher = DATE.matcher(stripped);
        if (matcher.find()) {
            return stripped.substring(0, matcher.start()).strip();
        }
        return stripped;
    }

    private static int levelOf(PdfExtractor.Line line, List<Double> levels, int maxLevel) {
        Matcher matcher = HEADING_NUMBER.matcher(line.text().strip());
        if (matcher.find()) {
            long dots = matcher.group().chars().filter(c -> c == '.').count();
            return (int) Math.min(Math.max(dots, 1), maxLevel);
        }
        int rank = levels.indexOf(round(line.fontSize()));
        return Math.min(Math.max(rank + 1, 1), maxLevel);
    }

    private static String[] sectionPath(List<SectionEntry> stack) {
        return stack.stream().map(SectionEntry::title).toArray(String[]::new);
    }

    private static double bodyFontSize(List<PdfExtractor.Page> pages) {
        Map<Double, Integer> histogram = new HashMap<>();
        for (PdfExtractor.Page page : pages) {
            for (PdfExtractor.Line line : page.lines()) {
                histogram.merge(round(line.fontSize()), Math.max(line.text().length(), 1), Integer::sum);
            }
        }
        return histogram.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(10.0);
    }

    private static double round(double size) {
        return Math.round(size * 2) / 2.0;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\d+", "#").replaceAll("\\s+", "").strip();
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF);
    }
}
