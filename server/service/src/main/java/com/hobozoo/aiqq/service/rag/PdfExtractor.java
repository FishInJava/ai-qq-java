package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/**
 * PDFBox 取文本与坐标，页内按基线聚成行。
 */
@Component
public class PdfExtractor {

    public record Cell(String text, double x0, double x1) {
    }

    public record Line(String text, double x0, double y0, double x1, double y1, double fontSize,
                       boolean bold, String fontName, List<Cell> cells, int digits) {
    }

    public record Page(int pageNo, double width, double height, int rotation, int charCount, List<Line> lines) {
    }

    public record Result(boolean locked, List<Page> pages) {
    }

    public Result extract(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            Collector collector = new Collector();
            collector.setSortByPosition(true);
            collector.getText(document);

            List<Page> pages = new ArrayList<>();
            int pageCount = document.getNumberOfPages();
            for (int index = 0; index < pageCount; index++) {
                PDPage page = document.getPage(index);
                int pageNo = index + 1;
                int rotation = page.getRotation();
                boolean sideways = rotation == 90 || rotation == 270;
                double cropWidth = page.getCropBox().getWidth();
                double cropHeight = page.getCropBox().getHeight();
                double width = sideways ? cropHeight : cropWidth;
                double height = sideways ? cropWidth : cropHeight;

                List<Line> lines = toLines(collector.positions.getOrDefault(pageNo, List.of()), height);
                int charCount = lines.stream().mapToInt(line -> line.text().length()).sum();
                pages.add(new Page(pageNo, width, height, rotation, charCount, lines));
            }
            return new Result(false, pages);
        } catch (InvalidPasswordException e) {
            return new Result(true, List.of());
        }
    }

    private static List<Line> toLines(List<TextPosition> positions, double pageHeight) {
        if (positions.isEmpty()) {
            return List.of();
        }
        List<TextPosition> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingDouble(TextPosition::getYDirAdj)
                .thenComparingDouble(TextPosition::getXDirAdj));

        List<List<TextPosition>> clusters = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();
        double anchor = 0;
        for (TextPosition position : sorted) {
            double y = position.getYDirAdj();
            double fontSize = Math.max(position.getFontSizeInPt(), 1);
            if (current.isEmpty() || Math.abs(y - anchor) <= fontSize * 0.6) {
                if (current.isEmpty()) {
                    anchor = y;
                }
                current.add(position);
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(position);
                anchor = y;
            }
        }
        if (!current.isEmpty()) {
            clusters.add(current);
        }

        List<Line> lines = new ArrayList<>(clusters.size());
        for (List<TextPosition> cluster : clusters) {
            cluster.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
            Line line = toLine(cluster, pageHeight);
            if (line != null && !line.text().isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static Line toLine(List<TextPosition> cluster, double pageHeight) {
        StringBuilder text = new StringBuilder();
        List<Cell> cells = new ArrayList<>();
        StringBuilder cellText = new StringBuilder();
        double x0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        double fontSize = 0;
        boolean bold = false;
        String fontName = null;
        int digits = 0;

        TextPosition previous = null;
        double cellStart = 0;
        for (TextPosition position : cluster) {
            String unicode = position.getUnicode();
            double left = position.getXDirAdj();
            double right = left + position.getWidthDirAdj();
            double glyphTop = position.getYDirAdj() - position.getHeightDir();
            double glyphBottom = position.getYDirAdj();
            double size = Math.max(position.getFontSizeInPt(), 1);

            if (previous != null && left - (previous.getXDirAdj() + previous.getWidthDirAdj()) > size * 0.25
                    && !text.isEmpty() && text.charAt(text.length() - 1) != ' ') {
                text.append(' ');
                cellText.append(' ');
            }
            if (previous != null && left - (previous.getXDirAdj() + previous.getWidthDirAdj()) > size * 1.5) {
                cells.add(new Cell(cellText.toString().trim(), cellStart, previous.getXDirAdj()
                        + previous.getWidthDirAdj()));
                cellText.setLength(0);
                cellStart = left;
            }
            if (cellText.isEmpty()) {
                cellStart = left;
            }
            text.append(unicode);
            cellText.append(unicode);
            if (Character.isDigit(unicode.charAt(0))) {
                digits++;
            }

            x0 = Math.min(x0, left);
            x1 = Math.max(x1, right);
            top = Math.min(top, glyphTop);
            bottom = Math.max(bottom, glyphBottom);
            fontSize = Math.max(fontSize, size);
            String name = position.getFont() == null ? "" : position.getFont().getName();
            if (name != null && name.toLowerCase().contains("bold")) {
                bold = true;
            }
            if (fontName == null && name != null && !name.isBlank()) {
                fontName = name;
            }
            previous = position;
        }
        if (cellText.length() > 0) {
            double lastRight = previous == null ? x1 : previous.getXDirAdj() + previous.getWidthDirAdj();
            cells.add(new Cell(cellText.toString().trim(), cellStart, lastRight));
        }
        if (text.isEmpty()) {
            return null;
        }
        double y0 = pageHeight - bottom;
        double y1 = pageHeight - top;
        return new Line(text.toString(), x0, y0, x1, y1, fontSize, bold, fontName, cells, digits);
    }

    private static final class Collector extends PDFTextStripper {

        private final Map<Integer, List<TextPosition>> positions = new LinkedHashMap<>();

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            positions.computeIfAbsent(getCurrentPageNo(), key -> new ArrayList<>()).addAll(textPositions);
        }
    }
}
