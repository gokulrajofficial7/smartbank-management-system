package com.smartbank.compliance;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.service.AuditService;
import com.smartbank.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndianBankingComplianceService {

    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final UnauthorizedTransactionComplaintRepository complaintRepository;

    // Statutory and Operational Thresholds
    public static final BigDecimal RTGS_MINIMUM_LIMIT = new BigDecimal("200000.00");
    public static final BigDecimal RTGS_OPERATIONAL_MAX = new BigDecimal("5000000.00");
    public static final BigDecimal UPI_STATUTORY_MAX = new BigDecimal("100000.00");
    public static final BigDecimal IMPS_NPCI_MAX = new BigDecimal("500000.00");
    public static final BigDecimal NEFT_OPERATIONAL_MAX = new BigDecimal("1000000.00");
    public static final BigDecimal INTERNAL_DEMO_MAX = new BigDecimal("50000.00");

    public static final BigDecimal RULE_114B_CASH_DEPOSIT_THRESHOLD = new BigDecimal("50000.00");
    public static final BigDecimal SECTION_269ST_THRESHOLD = new BigDecimal("200000.00");
    public static final BigDecimal RULE_114E_SFT_THRESHOLD = new BigDecimal("1000000.00"); // 10 Lakhs

    public static final BigDecimal SEC_194N_FILER_THRESHOLD = new BigDecimal("10000000.00"); // 1 Crore
    public static final BigDecimal SEC_194N_NON_FILER_LOWER = new BigDecimal("2000000.00"); // 20 Lakhs
    public static final BigDecimal SEC_194N_NON_FILER_UPPER = new BigDecimal("10000000.00"); // 1 Crore

    /**
     * Centralized Registry of all statutory, operational, and simulation rules.
     */
    public List<ComplianceRule> getAllComplianceRules() {
        List<ComplianceRule> rules = new ArrayList<>();

        // 1. RTGS Minimum
        rules.add(ComplianceRule.builder()
                .ruleCode("RBI-RTGS-MIN-01")
                .ruleName("RTGS Statutory Minimum Value Mandate")
                .description("RBI mandates a minimum transaction threshold of ₹2,00,000 for RTGS electronic transfers.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.RTGS)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .minThreshold(RTGS_MINIMUM_LIMIT)
                .maxThreshold(RTGS_OPERATIONAL_MAX)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("RBI Master Direction on RTGS System RBI/2020-21/73 DPSS.CO.RTGS.No.750/04.04.016/2020-21")
                .customerGuidance("RTGS is designated for high-value funds transfer. Minimum amount is ₹2,00,000.00.")
                .advisoryOnly(false)
                .build());

        // 2. UPI Standard Ceiling
        rules.add(ComplianceRule.builder()
                .ruleCode("RBI-UPI-CAP-01")
                .ruleName("UPI Per-Transaction Statutory Ceiling")
                .description("NPCI/RBI regulatory ceiling of ₹1,00,000 per standard UPI transaction.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.UPI)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .minThreshold(BigDecimal.ONE)
                .maxThreshold(UPI_STATUTORY_MAX)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("NPCI / RBI UPI Procedural & Operating Guidelines")
                .customerGuidance("Standard UPI transactions are capped at ₹1,00,000.00 per transaction.")
                .advisoryOnly(false)
                .build());

        // 3. IMPS Ceiling
        rules.add(ComplianceRule.builder()
                .ruleCode("NPCI-IMPS-CAP-01")
                .ruleName("IMPS Maximum Per-Transaction Limit")
                .description("NPCI procedural ceiling of ₹5,00,000 per transaction for IMPS 24x7 instant transfers.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.IMPS)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .minThreshold(BigDecimal.ONE)
                .maxThreshold(IMPS_NPCI_MAX)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("NPCI IMPS Operating Guidelines & Circulars")
                .customerGuidance("IMPS allows instant remittances up to ₹5,00,000.00.")
                .advisoryOnly(false)
                .build());

        // 4. NEFT Operational Ceiling
        rules.add(ComplianceRule.builder()
                .ruleCode("BANK-NEFT-CAP-01")
                .ruleName("NEFT Bank Operational Limit")
                .description("SmartBank internal operational limit of ₹10,00,000 per NEFT retail transfer.")
                .category(ComplianceCategory.BANK_OPERATIONAL)
                .channel(CompliancePaymentChannel.NEFT)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .minThreshold(BigDecimal.ONE)
                .maxThreshold(NEFT_OPERATIONAL_MAX)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("SmartBank Internal Operations Policy & RBI NEFT Procedural Guidelines")
                .customerGuidance("NEFT transfers within SmartBank are limited to ₹10,00,000.00 per retail transaction.")
                .advisoryOnly(false)
                .build());

        // 5. Internal Book Transfer Demo Limit
        rules.add(ComplianceRule.builder()
                .ruleCode("DEMO-INTERNAL-CAP-01")
                .ruleName("SmartBank Internal Demo Transfer Limit")
                .description("Simulated single transaction limit for internal intra-bank transfers.")
                .category(ComplianceCategory.SMARTBANK_DEMO)
                .channel(CompliancePaymentChannel.INTERNAL)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .minThreshold(BigDecimal.ONE)
                .maxThreshold(INTERNAL_DEMO_MAX)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("SmartBank Internship Demonstration Configuration")
                .customerGuidance("Single demo transfer limit is ₹50,000.00.")
                .advisoryOnly(false)
                .build());

        // 6. Income Tax Rule 114B (PAN Requirement on Cash Deposit)
        rules.add(ComplianceRule.builder()
                .ruleCode("IT-RULE-114B-PAN")
                .ruleName("Income Tax Rule 114B - PAN/Form 60 on Cash Deposits")
                .description("Mandatory PAN or Form 60 declaration for cash deposits exceeding ₹50,000 in a single day.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.BRANCH_CASH)
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .minThreshold(RULE_114B_CASH_DEPOSIT_THRESHOLD)
                .maxThreshold(null)
                .aggregationPeriod(AggregationPeriod.DAILY)
                .legalCitation("Rule 114B, Income-tax Rules, 1962 & CBDT Notifications")
                .customerGuidance("Deposits exceeding ₹50,000 in a day require a valid PAN or Form 60 on bank record.")
                .advisoryOnly(false)
                .build());

        // 7. Income Tax Section 269ST (Exemption on Own Bank Account Deposit)
        rules.add(ComplianceRule.builder()
                .ruleCode("IT-SEC-269ST-EXEMPT")
                .ruleName("Income Tax Section 269ST - Banking Company Receipt Exemption")
                .description("Receipt of cash of ₹2,00,000 or more by a banking company for deposit into an account holder's own account is exempt under Section 269ST(iii).")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.BRANCH_CASH)
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .minThreshold(SECTION_269ST_THRESHOLD)
                .maxThreshold(null)
                .aggregationPeriod(AggregationPeriod.DAILY)
                .legalCitation("Section 269ST(iii), Income-tax Act, 1961")
                .customerGuidance("Deposits of ₹2,00,000 or more into one's own bank account are statutory exceptions to cash receipt bans.")
                .advisoryOnly(true)
                .build());

        // 8. Income Tax Rule 114E - Statement of Financial Transactions (SFT-005)
        rules.add(ComplianceRule.builder()
                .ruleCode("IT-RULE-114E-SFT")
                .ruleName("Income Tax Rule 114E - SFT Reporting for Cash Deposits ≥ ₹10 Lakh")
                .description("Advisory reporting trigger when aggregate cash deposits in savings accounts equal or exceed ₹10,00,000 in a Financial Year.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.BRANCH_CASH)
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .minThreshold(RULE_114E_SFT_THRESHOLD)
                .maxThreshold(null)
                .aggregationPeriod(AggregationPeriod.ANNUAL_FINANCIAL_YEAR)
                .legalCitation("Rule 114E, Income-tax Rules, 1962 (Form 61A / Code SFT-005)")
                .customerGuidance("Aggregate annual cash deposits of ₹10 Lakh or more are reportable under SFT guidelines.")
                .advisoryOnly(true)
                .build());

        // 9. Income Tax Section 194N (TDS on Cash Withdrawals)
        rules.add(ComplianceRule.builder()
                .ruleCode("IT-SEC-194N-TDS")
                .ruleName("Income Tax Section 194N - TDS on High-Value Cash Withdrawals")
                .description("Simulated TDS withholding on aggregate cash withdrawals in excess of ₹1 Crore (or ₹20 Lakh for non-filers) in a Financial Year.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(CompliancePaymentChannel.ATM)
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .minThreshold(SEC_194N_NON_FILER_LOWER)
                .maxThreshold(null)
                .aggregationPeriod(AggregationPeriod.ANNUAL_FINANCIAL_YEAR)
                .legalCitation("Section 194N, Income-tax Act, 1961")
                .customerGuidance("Section 194N TDS applies to cash withdrawals above statutory limits in a financial year.")
                .advisoryOnly(false)
                .build());

        // 10. RBI Customer Liability Framework
        rules.add(ComplianceRule.builder()
                .ruleCode("RBI-UNAUTHORIZED-01")
                .ruleName("RBI Customer Protection - Limiting Liability in Unauthorized Electronic Transactions")
                .description("Zero liability for notification within 3 working days; tiered limited liability (₹5k/₹10k/₹25k) for 4-7 working days; 10-day shadow credit and 90-day resolution mandates.")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(null)
                .transactionType(Transaction.TransactionType.TRANSFER)
                .minThreshold(null)
                .maxThreshold(null)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("RBI Circular DBR.No.Leg.BC.78/09.07.005/2017-18")
                .customerGuidance("Report unauthorized electronic transactions promptly to minimize financial liability.")
                .advisoryOnly(false)
                .build());

        // 11. Positive Pay System
        rules.add(ComplianceRule.builder()
                .ruleCode("RBI-PPS-01")
                .ruleName("Positive Pay System (PPS) for Cheque Truncation")
                .description("CTS reconfirmation of key details for cheques of ₹50,000+ (voluntary) and ₹5,00,000+ (mandatory at bank discretion).")
                .category(ComplianceCategory.RBI_STATUTORY)
                .channel(null)
                .transactionType(null)
                .minThreshold(new BigDecimal("50000.00"))
                .maxThreshold(null)
                .aggregationPeriod(AggregationPeriod.PER_TRANSACTION)
                .legalCitation("RBI Circular DPSS.CO.RPPD.No.309/04.07.005/2020-21")
                .customerGuidance("Submit Positive Pay details before issuing cheques of ₹50,000 and above.")
                .advisoryOnly(true)
                .build());

        return Collections.unmodifiableList(rules);
    }

    /**
     * Validate fund transfer against channel-specific regulatory rules.
     */
    public void validateTransfer(Customer sender, Customer receiver, BigDecimal amount, CompliancePaymentChannel channel) {
        if (channel == null) {
            channel = CompliancePaymentChannel.INTERNAL;
        }

        switch (channel) {
            case RTGS -> {
                if (amount.compareTo(RTGS_MINIMUM_LIMIT) < 0) {
                    log.warn("[COMPLIANCE-REJECT] RTGS transfer of {} below minimum threshold {}", amount, RTGS_MINIMUM_LIMIT);
                    throw new BankingException("RTGS Statutory Compliance Error: Real Time Gross Settlement (RTGS) requires a minimum amount of " +
                            CurrencyUtil.format(RTGS_MINIMUM_LIMIT) + " per transaction under RBI Master Directions (RBI/2020-21/73). For amounts below " +
                            CurrencyUtil.format(RTGS_MINIMUM_LIMIT) + ", please select NEFT, IMPS, or UPI.");
                }
                if (amount.compareTo(RTGS_OPERATIONAL_MAX) > 0) {
                    log.warn("[COMPLIANCE-REJECT] RTGS transfer of {} exceeds operational ceiling {}", amount, RTGS_OPERATIONAL_MAX);
                    throw new BankingException("RTGS Operational Limit Error: SmartBank per-transaction RTGS limit is " +
                            CurrencyUtil.format(RTGS_OPERATIONAL_MAX) + ". For transactions above this value, please contact your branch manager.");
                }
            }
            case UPI -> {
                if (amount.compareTo(UPI_STATUTORY_MAX) > 0) {
                    log.warn("[COMPLIANCE-REJECT] UPI transfer of {} exceeds statutory ceiling {}", amount, UPI_STATUTORY_MAX);
                    throw new BankingException("UPI Statutory Compliance Error: Maximum transaction limit for UPI is " +
                            CurrencyUtil.format(UPI_STATUTORY_MAX) + " as prescribed by NPCI/RBI regulations. For higher amounts, please use IMPS, NEFT, or RTGS.");
                }
            }
            case IMPS -> {
                if (amount.compareTo(IMPS_NPCI_MAX) > 0) {
                    log.warn("[COMPLIANCE-REJECT] IMPS transfer of {} exceeds ceiling {}", amount, IMPS_NPCI_MAX);
                    throw new BankingException("IMPS Statutory Compliance Error: Maximum limit for Immediate Payment Service (IMPS) is " +
                            CurrencyUtil.format(IMPS_NPCI_MAX) + " per transaction under NPCI guidelines. For amounts exceeding " +
                            CurrencyUtil.format(IMPS_NPCI_MAX) + ", please use NEFT or RTGS.");
                }
            }
            case NEFT -> {
                if (amount.compareTo(NEFT_OPERATIONAL_MAX) > 0) {
                    log.warn("[COMPLIANCE-REJECT] NEFT transfer of {} exceeds bank ceiling {}", amount, NEFT_OPERATIONAL_MAX);
                    throw new BankingException("NEFT Operational Limit Error: SmartBank per-transaction limit for retail NEFT is " +
                            CurrencyUtil.format(NEFT_OPERATIONAL_MAX) + ". For higher-value transactions, please select RTGS (minimum " +
                            CurrencyUtil.format(RTGS_MINIMUM_LIMIT) + ").");
                }
            }
            case INTERNAL -> {
                // Internal transfer demo rules (standard limit verification is done in TransactionLimitService)
                log.debug("Internal book transfer validated for amount {}", amount);
            }
            default -> {
                // Other channels
            }
        }
    }

    /**
     * Validate cash deposit under Income Tax Rule 114B, Section 269ST, and Rule 114E (SFT).
     */
    public void validateCashDeposit(Customer customer, BigDecimal amount) {
        if (customer == null || amount == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 1. Calculate cumulative cash deposits for today
        BigDecimal todayDeposits = transactionRepository.sumDepositsForCustomerInPeriod(customer.getId(), startOfDay, endOfDay);
        BigDecimal projectedDailyDeposits = todayDeposits.add(amount);

        // Rule 114B PAN Requirement
        if (projectedDailyDeposits.compareTo(RULE_114B_CASH_DEPOSIT_THRESHOLD) > 0) {
            boolean hasPan = customer.getPanNumber() != null && !customer.getPanNumber().trim().isEmpty();
            boolean hasForm60 = Boolean.TRUE.equals(customer.getForm60Submitted());

            if (!hasPan && !hasForm60) {
                log.warn("[COMPLIANCE-REJECT] Cash deposit of {} brings daily total to {} without PAN/Form 60 for customer {}",
                        amount, projectedDailyDeposits, customer.getAccountNumber());
                throw new BankingException("Income Tax Rule 114B Compliance Violation: Cash deposits exceeding ₹50,000.00 in a single day " +
                        "(Attempted: " + CurrencyUtil.format(amount) + ", Daily Cumulative: " + CurrencyUtil.format(projectedDailyDeposits) + ") " +
                        "require a valid PAN or Form 60 on bank record under Income-tax Rules, 1962. Please update your PAN in your customer profile or submit Form 60.");
            }
        }

        // Section 269ST Exemption Check
        if (amount.compareTo(SECTION_269ST_THRESHOLD) >= 0) {
            // Note: Section 269ST prohibits cash receipts of ₹2,00,000+ EXCEPT receipt by banking companies (clause iii).
            // A customer depositing cash into their own account is 100% EXEMPT under Section 269ST(iii).
            log.info("[COMPLIANCE-ADVISORY] Section 269ST Exemption: Cash deposit of {} into customer's own account {} is exempt under Section 269ST(iii).",
                    CurrencyUtil.format(amount), customer.getAccountNumber());
        }

        // Rule 114E SFT Reporting Trigger Check (Advisory Trigger only, does NOT block)
        LocalDate fyStart = getFinancialYearStartDate(today);
        LocalDate fyEnd = getFinancialYearEndDate(today);
        BigDecimal fyDeposits = transactionRepository.sumDepositsForCustomerInPeriod(customer.getId(), fyStart.atStartOfDay(), fyEnd.atTime(LocalTime.MAX));
        BigDecimal projectedFyDeposits = fyDeposits.add(amount);

        if (projectedFyDeposits.compareTo(RULE_114E_SFT_THRESHOLD) >= 0) {
            log.info("[COMPLIANCE-SFT] Rule 114E SFT Trigger: Aggregate cash deposits of {} in FY {} reached/exceeded ₹10,00,000 for customer {}",
                    CurrencyUtil.format(projectedFyDeposits), fyStart.getYear() + "-" + fyEnd.getYear(), customer.getAccountNumber());
            auditService.logEvent(
                    extractUserId(customer),
                    extractUsername(customer),
                    "CUSTOMER",
                    "SFT_REPORTABLE",
                    "COMPLIANCE",
                    customer.getAccountNumber(),
                    "Rule 114E SFT Trigger: Cumulative cash deposits of " + CurrencyUtil.format(projectedFyDeposits) +
                            " in FY reached or exceeded ₹10,00,000 threshold. Transaction reportable under Form 61A (Code SFT-005).",
                    "127.0.0.1",
                    "SUCCESS"
            );
        }
    }

    /**
     * Calculate Section 194N TDS on cash withdrawals across the Financial Year.
     */
    public TdsCalculationResult calculateSection194nCashWithdrawalTds(Customer customer, BigDecimal withdrawalAmount) {
        LocalDate today = LocalDate.now();
        LocalDate fyStart = getFinancialYearStartDate(today);
        LocalDate fyEnd = getFinancialYearEndDate(today);

        BigDecimal previousWithdrawals = transactionRepository.sumWithdrawalsForCustomerInPeriod(
                customer.getId(), fyStart.atStartOfDay(), fyEnd.atTime(LocalTime.MAX));
        BigDecimal newCumulative = previousWithdrawals.add(withdrawalAmount);
        boolean isFiler = customer.getItrFiler() == null || customer.getItrFiler();

        BigDecimal tdsAmount = BigDecimal.ZERO;
        BigDecimal slice2Percent = BigDecimal.ZERO;
        BigDecimal slice5Percent = BigDecimal.ZERO;
        StringBuilder explanation = new StringBuilder();

        if (isFiler) {
            // ITR Filer: 2% on amount exceeding ₹1 Crore
            BigDecimal prevTaxable = previousWithdrawals.subtract(SEC_194N_FILER_THRESHOLD).max(BigDecimal.ZERO);
            BigDecimal newTaxable = newCumulative.subtract(SEC_194N_FILER_THRESHOLD).max(BigDecimal.ZERO);
            slice2Percent = newTaxable.subtract(prevTaxable).max(BigDecimal.ZERO);
            tdsAmount = slice2Percent.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);

            if (tdsAmount.compareTo(BigDecimal.ZERO) > 0) {
                explanation.append("Section 194N TDS (Simulated): Customer is an ITR filer with FY cash withdrawals exceeding ₹1 Crore (")
                        .append(CurrencyUtil.format(newCumulative)).append("). 2% TDS deducted on taxable portion ")
                        .append(CurrencyUtil.format(slice2Percent)).append(": ").append(CurrencyUtil.format(tdsAmount));
            } else {
                explanation.append("Section 194N: No TDS applicable. Cumulative FY withdrawals (")
                        .append(CurrencyUtil.format(newCumulative)).append(") are within the ₹1 Crore threshold for ITR filers.");
            }
        } else {
            // Non-Filer:
            // Up to ₹20L: Nil
            // Between ₹20L and ₹1Cr: 2%
            // Above ₹1Cr: 5%
            BigDecimal prevTax = calculateNonFilerCumulativeTax(previousWithdrawals);
            BigDecimal newTax = calculateNonFilerCumulativeTax(newCumulative);
            tdsAmount = newTax.subtract(prevTax).setScale(2, RoundingMode.HALF_UP);

            // Compute slices for transparency
            BigDecimal prevSlice2 = computeNonFilerSlice2(previousWithdrawals);
            BigDecimal newSlice2 = computeNonFilerSlice2(newCumulative);
            slice2Percent = newSlice2.subtract(prevSlice2).max(BigDecimal.ZERO);

            BigDecimal prevSlice5 = computeNonFilerSlice5(previousWithdrawals);
            BigDecimal newSlice5 = computeNonFilerSlice5(newCumulative);
            slice5Percent = newSlice5.subtract(prevSlice5).max(BigDecimal.ZERO);

            if (tdsAmount.compareTo(BigDecimal.ZERO) > 0) {
                explanation.append("Section 194N TDS (Simulated): Non-ITR filer status. Total TDS: ")
                        .append(CurrencyUtil.format(tdsAmount))
                        .append(" (2% on ").append(CurrencyUtil.format(slice2Percent))
                        .append(slice5Percent.compareTo(BigDecimal.ZERO) > 0 ? " + 5% on " + CurrencyUtil.format(slice5Percent) : "")
                        .append(").");
            } else {
                explanation.append("Section 194N: No TDS applicable. Cumulative FY withdrawals (")
                        .append(CurrencyUtil.format(newCumulative)).append(") are within the ₹20 Lakh threshold for non-filers.");
            }
        }

        BigDecimal netPayout = withdrawalAmount.subtract(tdsAmount);

        return TdsCalculationResult.builder()
                .grossAmount(withdrawalAmount)
                .previousCumulativeWithdrawals(previousWithdrawals)
                .newCumulativeWithdrawals(newCumulative)
                .isItrFiler(isFiler)
                .taxableSlice2Percent(slice2Percent)
                .taxableSlice5Percent(slice5Percent)
                .tdsAmount(tdsAmount)
                .netPayout(netPayout)
                .legalCitation("Section 194N, Income-tax Act, 1961")
                .explanation(explanation.toString())
                .build();
    }

    private BigDecimal calculateNonFilerCumulativeTax(BigDecimal total) {
        if (total.compareTo(SEC_194N_NON_FILER_LOWER) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal tax = BigDecimal.ZERO;
        if (total.compareTo(SEC_194N_NON_FILER_UPPER) <= 0) {
            // Portion between 20L and total taxed at 2%
            BigDecimal portion = total.subtract(SEC_194N_NON_FILER_LOWER);
            tax = portion.multiply(new BigDecimal("0.02"));
        } else {
            // Portion between 20L and 1Cr (80 Lakhs) taxed at 2%
            BigDecimal slice1 = SEC_194N_NON_FILER_UPPER.subtract(SEC_194N_NON_FILER_LOWER);
            BigDecimal tax1 = slice1.multiply(new BigDecimal("0.02")); // ₹1,60,000

            // Portion above 1Cr taxed at 5%
            BigDecimal slice2 = total.subtract(SEC_194N_NON_FILER_UPPER);
            BigDecimal tax2 = slice2.multiply(new BigDecimal("0.05"));

            tax = tax1.add(tax2);
        }
        return tax;
    }

    private BigDecimal computeNonFilerSlice2(BigDecimal total) {
        if (total.compareTo(SEC_194N_NON_FILER_LOWER) <= 0) {
            return BigDecimal.ZERO;
        }
        return total.min(SEC_194N_NON_FILER_UPPER).subtract(SEC_194N_NON_FILER_LOWER);
    }

    private BigDecimal computeNonFilerSlice5(BigDecimal total) {
        if (total.compareTo(SEC_194N_NON_FILER_UPPER) <= 0) {
            return BigDecimal.ZERO;
        }
        return total.subtract(SEC_194N_NON_FILER_UPPER);
    }

    /**
     * File an unauthorized electronic banking transaction complaint under RBI framework.
     */
    @Transactional
    public UnauthorizedTransactionComplaint fileUnauthorizedTransactionComplaint(
            Customer customer,
            String transactionReference,
            BigDecimal disputeAmount,
            LocalDateTime incidentDate,
            LocalDateTime reportedDate,
            String customerDescription) {

        if (disputeAmount == null || disputeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Dispute amount must be greater than zero.");
        }
        if (incidentDate == null || reportedDate == null) {
            throw new BankingException("Incident date and reporting date are required.");
        }
        if (reportedDate.isBefore(incidentDate)) {
            throw new BankingException("Reported date cannot be prior to incident date.");
        }

        int daysDifference = (int) ChronoUnit.DAYS.between(incidentDate.toLocalDate(), reportedDate.toLocalDate());
        String liabilityTier;
        BigDecimal customerCap;
        BigDecimal bankLiability;

        if (daysDifference <= 3) {
            // Zero Liability: Notified within 3 working days
            liabilityTier = "ZERO_LIABILITY";
            customerCap = BigDecimal.ZERO;
            bankLiability = disputeAmount;
        } else if (daysDifference <= 7) {
            // Limited Liability: Notified within 4 to 7 working days
            liabilityTier = "LIMITED_LIABILITY";
            // Standard Savings Account Cap per RBI circular is ₹10,000.00
            BigDecimal maxCap = new BigDecimal("10000.00");
            customerCap = disputeAmount.min(maxCap);
            bankLiability = disputeAmount.subtract(customerCap);
        } else {
            // Beyond 7 working days: Determined by Bank Board approved policy
            liabilityTier = "BOARD_POLICY";
            customerCap = disputeAmount;
            bankLiability = BigDecimal.ZERO;
        }

        LocalDate shadowReversalDeadline = reportedDate.toLocalDate().plusDays(10);
        LocalDate resolutionDeadline = reportedDate.toLocalDate().plusDays(90);

        String refNum = "CMP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        UnauthorizedTransactionComplaint complaint = UnauthorizedTransactionComplaint.builder()
                .complaintReference(refNum)
                .customer(customer)
                .transactionReference(transactionReference)
                .disputeAmount(disputeAmount)
                .incidentDate(incidentDate)
                .reportedDate(reportedDate)
                .daysToReport(daysDifference)
                .liabilityTier(liabilityTier)
                .customerLiabilityCap(customerCap)
                .bankLiabilityAmount(bankLiability)
                .shadowReversalDeadline(shadowReversalDeadline)
                .resolutionDeadline(resolutionDeadline)
                .status(ComplaintStatus.SUBMITTED)
                .customerDescription(customerDescription)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        UnauthorizedTransactionComplaint saved = complaintRepository.save(complaint);

        auditService.logEvent(
                extractUserId(customer),
                extractUsername(customer),
                "CUSTOMER",
                "UNAUTHORIZED_TRANSACTION_REPORTED",
                "COMPLAINT",
                saved.getComplaintReference(),
                "Filed unauthorized transaction complaint " + refNum + " for " + CurrencyUtil.format(disputeAmount) +
                        ". Days to report: " + daysDifference + ", Tier: " + liabilityTier +
                        ", Customer Cap: " + CurrencyUtil.format(customerCap),
                "127.0.0.1",
                "SUCCESS"
        );

        log.info("Filed unauthorized transaction complaint {} for customer {}: tier={}, customerCap={}",
                refNum, customer.getAccountNumber(), liabilityTier, customerCap);

        return saved;
    }

    public List<UnauthorizedTransactionComplaint> getCustomerComplaints(Customer customer) {
        if (customer == null) {
            return Collections.emptyList();
        }
        return complaintRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    public List<UnauthorizedTransactionComplaint> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    private Long extractUserId(Customer customer) {
        if (customer == null) return null;
        try {
            if (customer.getUser() != null) {
                return customer.getUser().getId();
            }
        } catch (Exception ignored) {
            // Lazy proxy detached
        }
        return null;
    }

    private String extractUsername(Customer customer) {
        if (customer == null) return "SYSTEM";
        try {
            if (customer.getUser() != null) {
                return customer.getUser().getUsername();
            }
        } catch (Exception ignored) {
            // Lazy proxy detached
        }
        return "SYSTEM";
    }

    /**
     * Utility to determine Indian Financial Year start date (April 1).
     */
    public LocalDate getFinancialYearStartDate(LocalDate date) {
        int year = (date.getMonthValue() >= 4) ? date.getYear() : date.getYear() - 1;
        return LocalDate.of(year, 4, 1);
    }

    /**
     * Utility to determine Indian Financial Year end date (March 31).
     */
    public LocalDate getFinancialYearEndDate(LocalDate date) {
        int year = (date.getMonthValue() >= 4) ? date.getYear() + 1 : date.getYear();
        return LocalDate.of(year, 3, 31);
    }
}
