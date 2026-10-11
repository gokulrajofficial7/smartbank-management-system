package com.smartbank.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Breakdown of Income Tax Section 194N TDS calculation on cash withdrawals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TdsCalculationResult {

    private BigDecimal grossAmount;
    private BigDecimal previousCumulativeWithdrawals;
    private BigDecimal newCumulativeWithdrawals;
    private boolean isItrFiler;
    private BigDecimal taxableSlice2Percent;
    private BigDecimal taxableSlice5Percent;
    private BigDecimal tdsAmount;
    private BigDecimal netPayout;
    private String legalCitation;
    private String explanation;
}
