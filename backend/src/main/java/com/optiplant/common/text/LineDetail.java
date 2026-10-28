package com.optiplant.common.text;

/**
 * Messages about one line of a document read «Línea N: <detail>», with the detail continuing the sentence in lowercase
 * («Línea 1: el producto…»). An acronym or code at the start («SKU…») keeps its case.
 */
public final class LineDetail {
    private LineDetail() {}

    public static String of(Object number, String detail) {
        boolean word = detail.length() > 1 && Character.isUpperCase(detail.charAt(0)) && Character.isLowerCase(detail.charAt(1));
        return "Línea " + number + ": " + (word ? Character.toLowerCase(detail.charAt(0)) + detail.substring(1) : detail);
    }
}
