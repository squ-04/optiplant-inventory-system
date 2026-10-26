package com.optiplant.catalog.dto;

/** Exact base equivalent of a quantity expressed in a commercial unit, with the factor in force when it was computed. */
public record UnitConversionResponse(String unitName, String quantity, String factor, String baseUnitName, String baseQuantity) {}
