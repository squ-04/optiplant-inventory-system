package com.optiplant.reports.service;

import com.optiplant.reports.service.ReportTable.Column;
import com.optiplant.reports.service.ReportTable.Kind;
import com.optiplant.reports.service.ReportTable.Metadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Writes any {@link ReportTable} as a real XLSX workbook (RF-0051): title and metadata on top, then a frozen header with
 * filters and one row per record. Text always goes in string cells, so user content starting with {@code =} is never
 * interpreted as a formula (ARQ-010). A decimal goes in a numeric cell only when a double keeps it exactly; otherwise it
 * stays as text instead of being degraded.
 */
@Component
public class XlsxReportWriter {
    /** The significant digits a spreadsheet keeps in a numeric cell. */
    private static final int MAX_NUMERIC_DIGITS = 15;
    private static final byte[] HEADER_FILL = {0x14, 0x21, 0x3D};

    public byte[] write(ReportTable table) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream file = new ByteArrayOutputStream()) {
            workbook.getProperties().getCoreProperties().setCreator("OptiPlant");
            Sheet sheet = workbook.createSheet(table.sheet());
            Map<Kind, CellStyle> styles = styles(workbook);
            int header = metadata(workbook, sheet, table);
            header(workbook, sheet, table.columns(), header);
            int next = header + 1;
            for (List<Object> values : table.rows()) {
                Row row = sheet.createRow(next++);
                for (int i = 0; i < table.columns().size(); i++) cell(row, i, values.get(i), table.columns().get(i).kind(), styles);
            }
            sheet.createFreezePane(0, header + 1);
            sheet.setAutoFilter(new CellRangeAddress(header, next - 1, 0, table.columns().size() - 1));
            workbook.write(file);
            return file.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Returns the index of the header row. */
    private static int metadata(Workbook workbook, Sheet sheet, ReportTable table) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        CellStyle label = workbook.createCellStyle();
        label.setFont(labelFont);

        Cell heading = sheet.createRow(0).createCell(0);
        heading.setCellValue(table.title());
        heading.setCellStyle(title);
        int next = 1;
        for (Metadata item : table.metadata()) {
            Row row = sheet.createRow(next++);
            Cell name = row.createCell(0);
            name.setCellValue(item.label());
            name.setCellStyle(label);
            row.createCell(1).setCellValue(item.value());
        }
        return next + 1;
    }

    private static void header(XSSFWorkbook workbook, Sheet sheet, List<Column> columns, int index) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(HEADER_FILL));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        Row row = sheet.createRow(index);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columns.get(i).header());
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, columns.get(i).width() * 256);
        }
    }

    private static Map<Kind, CellStyle> styles(Workbook workbook) {
        Map<Kind, CellStyle> styles = new EnumMap<>(Kind.class);
        styles.put(Kind.DATE_TIME, format(workbook, "yyyy-mm-dd hh:mm:ss"));
        styles.put(Kind.QUANTITY, format(workbook, "#,##0.000"));
        styles.put(Kind.MONEY, format(workbook, "#,##0.00"));
        styles.put(Kind.DECIMAL, format(workbook, "General"));
        return styles;
    }

    private static CellStyle format(Workbook workbook, String pattern) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(pattern));
        return style;
    }

    private static void cell(Row row, int index, Object value, Kind kind, Map<Kind, CellStyle> styles) {
        if (value == null) return;
        Cell cell = row.createCell(index);
        switch (value) {
            case LocalDateTime date -> {
                cell.setCellValue(date);
                cell.setCellStyle(styles.get(kind));
            }
            case BigDecimal number when number.stripTrailingZeros().precision() <= MAX_NUMERIC_DIGITS -> {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(styles.get(kind));
            }
            case BigDecimal number -> cell.setCellValue(number.toPlainString());
            default -> cell.setCellValue(value.toString());
        }
    }
}
