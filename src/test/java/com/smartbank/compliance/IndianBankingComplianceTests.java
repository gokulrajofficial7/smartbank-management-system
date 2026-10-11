package com.smartbank.compliance;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AuditLogRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class IndianBankingComplianceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IndianBankingComplianceService complianceService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // =========================================================================
    // 1. PAYMENT CHANNEL SPECIFIC VALIDATION (RBI & NPCI MANDATES)
    // =========================================================================

    @Test
    @DisplayName("RTGS Compliance: Transfers below ₹2,00,000 must be rejected under RBI Master Directions")
    void testRtgsMinimumAmountValidation() {
        Customer sender = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        Customer receiver = customerRepository.findByAccountNumber("SB7505485085").orElseThrow();

        // Amount below ₹2,00,000
        BigDecimal amountBelowMin = new BigDecimal("150000.00");

        BankingException ex = assertThrows(BankingException.class, () ->
                complianceService.validateTransfer(sender, receiver, amountBelowMin, CompliancePaymentChannel.RTGS));

        assertTrue(ex.getMessage().contains("RTGS Statutory Compliance Error"), "Message should cite RTGS statutory compliance");
        assertTrue(ex.getMessage().contains("₹2,00,000.00"), "Message should mention ₹2,00,000 minimum");

        // Amount meeting/exceeding ₹2,00,000 must pass regulatory validation
        BigDecimal validRtgsAmount = new BigDecimal("250000.00");
        assertDoesNotThrow(() ->
                complianceService.validateTransfer(sender, receiver, validRtgsAmount, CompliancePaymentChannel.RTGS));
    }

    @Test
    @DisplayName("UPI Compliance: Transfers exceeding ₹1,00,000 must be rejected under NPCI/RBI guidelines")
    void testUpiMaximumCeilingValidation() {
        Customer sender = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        Customer receiver = customerRepository.findByAccountNumber("SB7505485085").orElseThrow();

        // Amount exceeding ₹1,00,000
        BigDecimal amountAboveCeiling = new BigDecimal("100001.00");

        BankingException ex = assertThrows(BankingException.class, () ->
                complianceService.validateTransfer(sender, receiver, amountAboveCeiling, CompliancePaymentChannel.UPI));

        assertTrue(ex.getMessage().contains("UPI Statutory Compliance Error"), "Message should cite UPI statutory compliance");
        assertTrue(ex.getMessage().contains("₹1,00,000.00"), "Message should mention ₹1,00,000 ceiling");

        // Amount within limit must pass
        BigDecimal validUpiAmount = new BigDecimal("50000.00");
        assertDoesNotThrow(() ->
                complianceService.validateTransfer(sender, receiver, validUpiAmount, CompliancePaymentChannel.UPI));
    }

    @Test
    @DisplayName("IMPS Compliance: Transfers exceeding ₹5,00,000 must be rejected under NPCI guidelines")
    void testImpsMaximumCeilingValidation() {
        Customer sender = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        Customer receiver = customerRepository.findByAccountNumber("SB7505485085").orElseThrow();

        // Amount exceeding ₹5,00,000
        BigDecimal amountAboveCeiling = new BigDecimal("500001.00");

        BankingException ex = assertThrows(BankingException.class, () ->
                complianceService.validateTransfer(sender, receiver, amountAboveCeiling, CompliancePaymentChannel.IMPS));

        assertTrue(ex.getMessage().contains("IMPS Statutory Compliance Error"), "Message should cite IMPS compliance");
        assertTrue(ex.getMessage().contains("₹5,00,000.00"), "Message should mention ₹5,00,000 ceiling");

        // Amount within limit must pass
        BigDecimal validImpsAmount = new BigDecimal("200000.00");
        assertDoesNotThrow(() ->
                complianceService.validateTransfer(sender, receiver, validImpsAmount, CompliancePaymentChannel.IMPS));
    }

    @Test
    @DisplayName("NEFT Compliance: Transfers exceeding operational ceiling of ₹10,00,000 must be rejected")
    void testNeftOperationalCeilingValidation() {
        Customer sender = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        Customer receiver = customerRepository.findByAccountNumber("SB7505485085").orElseThrow();

        BigDecimal amountAboveCeiling = new BigDecimal("1000001.00");

        BankingException ex = assertThrows(BankingException.class, () ->
                complianceService.validateTransfer(sender, receiver, amountAboveCeiling, CompliancePaymentChannel.NEFT));

        assertTrue(ex.getMessage().contains("NEFT Operational Limit Error"), "Message should cite NEFT operational limit");

        BigDecimal validNeftAmount = new BigDecimal("500000.00");
        assertDoesNotThrow(() ->
                complianceService.validateTransfer(sender, receiver, validNeftAmount, CompliancePaymentChannel.NEFT));
    }

    // =========================================================================
    // 2. CASH DEPOSIT & INCOME TAX RULES (RULE 114B, SEC 269ST, RULE 114E)
    // =========================================================================

    @Test
    @DisplayName("Income Tax Rule 114B: Cash deposits exceeding ₹50,000 in a day without PAN/Form 60 must be blocked")
    void testRule114bPanRequirementValidation() {
        // Customer Tharun has no PAN and has not submitted Form 60
        Customer tharun = customerRepository.findByAccountNumber("SB2929788625").orElseThrow();
        tharun.setPanNumber(null);
        tharun.setForm60Submitted(false);
        customerRepository.save(tharun);

        BigDecimal highDeposit = new BigDecimal("50001.00");

        BankingException ex = assertThrows(BankingException.class, () ->
                complianceService.validateCashDeposit(tharun, highDeposit));

        assertTrue(ex.getMessage().contains("Income Tax Rule 114B"), "Message should cite Rule 114B");
        assertTrue(ex.getMessage().contains("₹50,000.00"), "Message should mention ₹50,000 threshold");

        // Small deposit (<= ₹50,000) does not require PAN
        BigDecimal smallDeposit = new BigDecimal("20000.00");
        assertDoesNotThrow(() -> complianceService.validateCashDeposit(tharun, smallDeposit));

        // When PAN is provided, deposit > ₹50,000 must be permitted
        tharun.setPanNumber("ABCDE9999Z");
        customerRepository.save(tharun);
        assertDoesNotThrow(() -> complianceService.validateCashDeposit(tharun, highDeposit));

        // Reset tharun
        tharun.setPanNumber(null);
        customerRepository.save(tharun);
    }

    @Test
    @DisplayName("Income Tax Section 269ST Exemption: Deposit into own bank account is exempt under 269ST(iii)")
    void testSection269stOwnAccountDepositExemption() {
        Customer appu = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        BigDecimal cashDepositAbove2L = new BigDecimal("250000.00");

        // Section 269ST(iii) exempts banking companies from receipt prohibition. Must NOT throw!
        assertDoesNotThrow(() -> complianceService.validateCashDeposit(appu, cashDepositAbove2L));
    }

    // =========================================================================
    // 3. SECTION 194N TDS ON CASH WITHDRAWALS (FILER VS NON-FILER TIERS)
    // =========================================================================

    @Test
    @DisplayName("Section 194N: ITR Filer cash withdrawals calculation")
    void testSection194nFilerCalculation() {
        Customer filer = Customer.builder()
                .id(999L)
                .accountNumber("SB_TEST_FILER")
                .name("Filer Test")
                .itrFiler(true)
                .build();

        // 1. Withdrawal under ₹1 Crore -> 0% TDS
        TdsCalculationResult result1 = complianceService.calculateSection194nCashWithdrawalTds(filer, new BigDecimal("5000000.00")); // 50 Lakhs
        assertEquals(BigDecimal.ZERO.setScale(2), result1.getTdsAmount());
        assertEquals(new BigDecimal("5000000.00"), result1.getNetPayout());

        // 2. Withdrawal exceeding ₹1 Crore (e.g. ₹1.5 Crore) -> 2% on excess (₹50 Lakhs)
        TdsCalculationResult result2 = complianceService.calculateSection194nCashWithdrawalTds(filer, new BigDecimal("15000000.00")); // 1.5 Cr
        BigDecimal expectedTds = new BigDecimal("5000000.00").multiply(new BigDecimal("0.02")).setScale(2); // ₹1,00,000.00
        assertEquals(expectedTds, result2.getTdsAmount());
        assertEquals(new BigDecimal("14900000.00"), result2.getNetPayout());
    }

    @Test
    @DisplayName("Section 194N: Non-ITR Filer cash withdrawals calculation (2% above 20L, 5% above 1Cr)")
    void testSection194nNonFilerCalculation() {
        Customer nonFiler = Customer.builder()
                .id(998L)
                .accountNumber("SB_TEST_NONFILER")
                .name("Non-Filer Test")
                .itrFiler(false)
                .build();

        // 1. Withdrawal under ₹20 Lakhs -> 0% TDS
        TdsCalculationResult result1 = complianceService.calculateSection194nCashWithdrawalTds(nonFiler, new BigDecimal("1500000.00")); // 15 Lakhs
        assertEquals(BigDecimal.ZERO.setScale(2), result1.getTdsAmount());

        // 2. Withdrawal between ₹20L and ₹1Cr (e.g. ₹50 Lakhs) -> 2% on (50L - 20L = 30L) = ₹60,000
        TdsCalculationResult result2 = complianceService.calculateSection194nCashWithdrawalTds(nonFiler, new BigDecimal("5000000.00"));
        BigDecimal expectedTds2 = new BigDecimal("3000000.00").multiply(new BigDecimal("0.02")).setScale(2); // ₹60,000.00
        assertEquals(expectedTds2, result2.getTdsAmount());

        // 3. Withdrawal exceeding ₹1 Crore (e.g. ₹1.2 Crore) -> 2% on 80L (1.6L) + 5% on 20L (1.0L) = ₹2,60,000
        TdsCalculationResult result3 = complianceService.calculateSection194nCashWithdrawalTds(nonFiler, new BigDecimal("12000000.00"));
        BigDecimal expectedTds3 = new BigDecimal("160000.00").add(new BigDecimal("100000.00")).setScale(2); // ₹2,60,000.00
        assertEquals(expectedTds3, result3.getTdsAmount());
    }

    // =========================================================================
    // 4. RBI UNAUTHORIZED TRANSACTION COMPLAINT FRAMEWORK
    // =========================================================================

    @Test
    @DisplayName("RBI Customer Liability: <= 3 working days reports must qualify for ZERO_LIABILITY")
    void testZeroLiabilityWithinThreeDays() {
        Customer customer = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        LocalDateTime incident = LocalDateTime.now().minusDays(2);
        LocalDateTime reported = LocalDateTime.now();

        UnauthorizedTransactionComplaint complaint = complianceService.fileUnauthorizedTransactionComplaint(
                customer,
                "TXN-FRAUD-1",
                new BigDecimal("45000.00"),
                incident,
                reported,
                "Unauthorized online debit"
        );

        assertNotNull(complaint.getComplaintReference());
        assertEquals("ZERO_LIABILITY", complaint.getLiabilityTier());
        assertEquals(BigDecimal.ZERO, complaint.getCustomerLiabilityCap());
        assertEquals(new BigDecimal("45000.00"), complaint.getBankLiabilityAmount());
        assertNotNull(complaint.getShadowReversalDeadline());
        assertNotNull(complaint.getResolutionDeadline());
    }

    @Test
    @DisplayName("RBI Customer Liability: 4 to 7 working days reports qualify for LIMITED_LIABILITY with ₹10,000 cap")
    void testLimitedLiabilityFourToSevenDays() {
        Customer customer = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        LocalDateTime incident = LocalDateTime.now().minusDays(5);
        LocalDateTime reported = LocalDateTime.now();

        UnauthorizedTransactionComplaint complaint = complianceService.fileUnauthorizedTransactionComplaint(
                customer,
                "TXN-FRAUD-2",
                new BigDecimal("45000.00"),
                incident,
                reported,
                "Card stolen and debited 5 days ago"
        );

        assertEquals("LIMITED_LIABILITY", complaint.getLiabilityTier());
        assertEquals(new BigDecimal("10000.00"), complaint.getCustomerLiabilityCap());
        assertEquals(new BigDecimal("35000.00"), complaint.getBankLiabilityAmount());
    }

    @Test
    @DisplayName("RBI Customer Liability: Beyond 7 working days is governed by BOARD_POLICY")
    void testBoardPolicyBeyondSevenDays() {
        Customer customer = customerRepository.findByAccountNumber("SB3532006893").orElseThrow();
        LocalDateTime incident = LocalDateTime.now().minusDays(15);
        LocalDateTime reported = LocalDateTime.now();

        UnauthorizedTransactionComplaint complaint = complianceService.fileUnauthorizedTransactionComplaint(
                customer,
                "TXN-FRAUD-3",
                new BigDecimal("20000.00"),
                incident,
                reported,
                "Discovered old unauthorized charge"
        );

        assertEquals("BOARD_POLICY", complaint.getLiabilityTier());
        assertEquals(new BigDecimal("20000.00"), complaint.getCustomerLiabilityCap());
    }

    // =========================================================================
    // 5. WEB ENDPOINTS & ACCESS CONTROL
    // =========================================================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Customer Portal: Can view disputes page and rules matrix")
    void testCustomerCompliancePagesAccess() throws Exception {
        mockMvc.perform(get("/customer/complaints"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("complaints"));

        mockMvc.perform(get("/customer/positive-pay"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("accounts"));

        mockMvc.perform(get("/customer/compliance-rules"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rules"));
    }

    @Test
    @WithMockUser(username = "compliance_a", roles = {"COMPLIANCE_OFFICER"})
    @DisplayName("Compliance Portal: Officer can view regulatory rules and disputes queue")
    void testComplianceOfficerPagesAccess() throws Exception {
        mockMvc.perform(get("/compliance/rules"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rules"));

        mockMvc.perform(get("/compliance/complaints"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("complaints"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Web Flow: Customer can report an unauthorized transaction dispute")
    void testCustomerFilingComplaintViaWeb() throws Exception {
        mockMvc.perform(post("/customer/complaints/report")
                        .with(csrf())
                        .param("transactionReference", "TXN-DEMO-001")
                        .param("disputeAmount", "15000.00")
                        .param("incidentDate", LocalDateTime.now().minusDays(1).toString())
                        .param("customerDescription", "Unauthorized POS charge"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/complaints"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Web Flow: Customer can register Positive Pay confirmation for cheque")
    void testPositivePaySubmissionViaWeb() throws Exception {
        mockMvc.perform(post("/customer/positive-pay")
                        .with(csrf())
                        .param("accountNumber", "SB3532006893")
                        .param("chequeNumber", "123456")
                        .param("payeeName", "Vendor Services Ltd")
                        .param("amount", "75000.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/positive-pay"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Web Flow: Customer can update PAN declaration")
    void testTaxCompliancePanUpdateViaWeb() throws Exception {
        mockMvc.perform(post("/customer/profile/pan")
                        .with(csrf())
                        .param("panNumber", "ABCDE1234F"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/profile"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Service Integration: RTGS transfer under ₹2L must be rejected in CustomerService")
    void testCustomerServiceTransferChannelRtgsRejection() {
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer("SB3532006893", "SB7505485085",
                        new BigDecimal("50000.00"), "Low RTGS", CompliancePaymentChannel.RTGS));

        assertTrue(ex.getMessage().contains("RTGS Statutory Compliance Error"));
        assertTrue(ex.getMessage().contains("₹2,00,000.00"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Service Integration: UPI transfer exceeding ₹1L must be rejected in CustomerService")
    void testCustomerServiceTransferChannelUpiRejection() {
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer("SB3532006893", "SB7505485085",
                        new BigDecimal("120000.00"), "High UPI", CompliancePaymentChannel.UPI));

        assertTrue(ex.getMessage().contains("UPI Statutory Compliance Error"));
        assertTrue(ex.getMessage().contains("₹1,00,000.00"));
    }
}
