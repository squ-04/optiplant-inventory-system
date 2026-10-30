package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.common.exception.ApiException;
import com.optiplant.reports.service.ReportPeriod;
import com.optiplant.reports.service.ReportTable;
import com.optiplant.reports.service.ReportTable.Column;
import com.optiplant.reports.service.ReportTable.Kind;
import com.optiplant.reports.service.ReportTable.Metadata;
import com.optiplant.reports.service.XlsxReportWriter;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** The shared workbook writer (ARQ-010) and the 12-month limit (RF-0055), without a database. */
class ReportWorkbookTest {
    private final XlsxReportWriter writer = new XlsxReportWriter();

    private Row row(List<Object> values, Column... columns) throws Exception {
        byte[] file = writer.write(new ReportTable("Datos", "Título", List.of(new Metadata("Filas", "1")), List.of(columns), List.of(values)));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            return workbook.getSheetAt(0).getRow(workbook.getSheetAt(0).getLastRowNum());
        }
    }

    @Test void userTextIsNeverAFormula() throws Exception {
        Row row = row(List.of("=HYPERLINK(\"http://x\")", "+1", "@cmd"), new Column("A", Kind.TEXT, 10), new Column("B", Kind.TEXT, 10),
            new Column("C", Kind.TEXT, 10));
        for (int i = 0; i < 3; i++) assertThat(row.getCell(i).getCellType()).isEqualTo(CellType.STRING);
        assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=HYPERLINK(\"http://x\")");
    }

    @Test void decimalsAreNumericWhileExactAndTextBeyondThePrecisionOfASpreadsheet() throws Exception {
        Row row = row(List.of(new BigDecimal("1250.125"), new BigDecimal("999999999999999.999")),
            new Column("A", Kind.QUANTITY, 10), new Column("B", Kind.QUANTITY, 10));
        assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(1250.125);
        assertThat(row.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("#,##0.000");
        Cell exact = row.getCell(1);
        assertThat(exact.getCellType()).isEqualTo(CellType.STRING);
        assertThat(exact.getStringCellValue()).isEqualTo("999999999999999.999");
    }

    @Test void aDecimalWithoutFractionShowsNoTrailingSeparator() throws Exception {
        Row row = row(List.of(new BigDecimal("100")), new Column("A", Kind.DECIMAL, 10));
        assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(100);
        assertThat(row.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("General");
    }

    @Test void datesAreRealDatesAndAMissingValueLeavesTheCellEmpty() throws Exception {
        LocalDateTime date = LocalDateTime.of(2026, 10, 30, 8, 15, 30);
        Row row = row(Arrays.asList(date, null), new Column("A", Kind.DATE_TIME, 10), new Column("B", Kind.DATE_TIME, 10));
        assertThat(DateUtil.isCellDateFormatted(row.getCell(0))).isTrue();
        assertThat(row.getCell(0).getLocalDateTimeCellValue()).isEqualTo(date);
        assertThat(row.getCell(1)).isNull();
    }

    @Test void theHeaderIsFrozenAndFilterable() throws Exception {
        byte[] file = writer.write(new ReportTable("Datos", "Título", List.of(new Metadata("Filas", "0")),
            List.of(new Column("A", Kind.TEXT, 10)), List.of()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Datos");
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(workbook.getSheetAt(0).getPaneInformation().isFreezePane()).isTrue();
            assertThat(workbook.getSheetAt(0).getCTWorksheet().isSetAutoFilter()).isTrue();
        }
    }

    @Test void twelveCalendarMonthsAreAllowedAndOneMoreDayIsNot() {
        assertThat(ReportPeriod.parse("2026-01-01", "2026-12-31").to()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(ReportPeriod.parse("2026-03-15", "2027-03-14").from()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(ReportPeriod.parse("2026-06-01", "2026-06-01").to()).isEqualTo(LocalDate.of(2026, 6, 1));
        for (String[] period : List.of(new String[] {"2026-01-01", "2027-01-01"}, new String[] {"2026-03-15", "2027-03-15"})) {
            assertThatThrownBy(() -> ReportPeriod.parse(period[0], period[1])).isInstanceOf(ApiException.class).hasMessageContaining("12 meses");
        }
    }

    @Test void thePeriodResolvesToBogotaDaysWithoutTheNextDay() {
        ReportPeriod period = ReportPeriod.parse("2026-10-01", "2026-10-31");
        assertThat(period.start()).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }
}
