package com.smartbank.compliance;

/**
 * Period over which compliance thresholds are aggregated.
 */
public enum AggregationPeriod {
    PER_TRANSACTION("Per Transaction"),
    DAILY("Daily (Calendar Day)"),
    ANNUAL_FINANCIAL_YEAR("Annual (Indian Financial Year: Apr 1 - Mar 31)");

    private final String description;

    AggregationPeriod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
