package com.optiplant.reports.service;

import java.util.List;

/**
 * A report ready to be written, independent of its category: what describes it, its columns and its rows. A cell is a
 * {@code String}, a {@code java.math.BigDecimal} or a {@code java.time.LocalDateTime} according to its column, or null
 * when it does not apply.
 */
public record ReportTable(String sheet, String title, List<Metadata> metadata, List<Column> columns, List<List<Object>> rows) {
    /** How a column is presented; each kind keeps the precision of its values. */
    public enum Kind { TEXT, DATE_TIME, QUANTITY, MONEY, DECIMAL }

    /** {@code width} in characters: fixed, so that the size does not depend on the fonts of the server. */
    public record Column(String header, Kind kind, int width) {}

    public record Metadata(String label, String value) {}
}
