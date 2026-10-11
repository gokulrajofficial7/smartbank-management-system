package com.smartbank.compliance;

import com.smartbank.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Standard representation of a statutory, operational, or simulation compliance rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRule {

    private String ruleCode;
    private String ruleName;
    private String description;
    private ComplianceCategory category;
    private CompliancePaymentChannel channel;
    private Transaction.TransactionType transactionType;
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;
    private AggregationPeriod aggregationPeriod;
    private String legalCitation;
    private String customerGuidance;
    private boolean advisoryOnly;
}
