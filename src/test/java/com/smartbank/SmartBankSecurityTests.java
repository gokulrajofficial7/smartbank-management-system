package com.smartbank;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.ApprovalRequestRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.ApprovalService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SmartBankSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private com.smartbank.repository.KycDocumentRepository kycDocumentRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.smartbank.repository.TransactionRepository transactionRepository;

    @Autowired
    private com.smartbank.service.TransactionService transactionService;

    // ==========================================
    // 1. UNAUTHENTICATED ACCESS PROTECTION
    // ==========================================

    @Test
    @DisplayName("Unauthenticated access to customer dashboard redirects to login")
    void testUnauthenticatedCustomerDashboardRedirects() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Unauthenticated access to manager approvals redirects to login")
    void testUnauthenticatedManagerApprovalsRedirects() throws Exception {
        mockMvc.perform(get("/manager/approvals"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Unauthenticated access to admin dashboard redirects to login")
    void testUnauthenticatedAdminDashboardRedirects() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ==========================================
    // 2. CUSTOMER DATA ISOLATION & IDOR PREVENTION
    // ==========================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("IDOR Prevention: Customer A cannot access Customer B profile by ID")
    void testCustomerCannotAccessOtherCustomerById() throws Exception {
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        mockMvc.perform(get("/customers/" + customerB.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("IDOR Prevention: Customer A cannot view Customer B details by Account Number")
    void testCustomerCannotAccessOtherCustomerByAccountNumber() throws Exception {
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        mockMvc.perform(get("/customers/account/" + customerB.getAccountNumber()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("BOLA Prevention: Customer A cannot withdraw from Customer B account")
    void testCustomerCannotWithdrawFromOtherAccount() throws Exception {
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        mockMvc.perform(post("/withdraw")
                        .with(csrf())
                        .param("accountNumber", customerB.getAccountNumber())
                        .param("amount", "100.00")
                        .param("description", "Unauthorized withdrawal attempt"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("BOLA Prevention: Customer A cannot transfer from Customer B account as sender")
    void testCustomerCannotTransferFromOtherAccount() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        mockMvc.perform(post("/transfer")
                        .with(csrf())
                        .param("senderAccount", customerB.getAccountNumber())
                        .param("receiverAccount", customerA.getAccountNumber())
                        .param("amount", "50.00")
                        .param("description", "Unauthorized transfer attempt"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 3. ROLE-BASED ACCESS CONTROL (RBAC)
    // ==========================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("RBAC: Customer cannot access staff portal")
    void testCustomerCannotAccessStaffPortal() throws Exception {
        mockMvc.perform(get("/staff/teller"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "teller_a", roles = {"TELLER"})
    @DisplayName("RBAC: Teller can access teller portal")
    void testTellerCanAccessTellerPortal() throws Exception {
        mockMvc.perform(get("/staff/teller"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "teller_a", roles = {"TELLER"})
    @DisplayName("RBAC: Teller cannot access manager approvals")
    void testTellerCannotAccessManagerApprovals() throws Exception {
        mockMvc.perform(get("/manager/approvals"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("RBAC: Manager can access manager approvals queue")
    void testManagerCanAccessApprovals() throws Exception {
        mockMvc.perform(get("/manager/approvals"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("RBAC: Manager cannot access admin dashboard")
    void testManagerCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_a", roles = {"BANK_ADMIN"})
    @DisplayName("RBAC: Admin can access admin dashboard")
    void testAdminCanAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 4. MAKER-CHECKER DUAL CONTROL
    // ==========================================

    @Test
    @DisplayName("Maker-Checker: Maker cannot approve their own transaction (maker != checker)")
    void testMakerCannotApproveOwnTransaction() {
        User maker = userRepository.findByUsername("teller_a").orElseThrow();

        ApprovalRequest request = approvalService.submitForApproval(
                null,
                maker,
                ApprovalType.HIGH_VALUE_TRANSACTION,
                new BigDecimal("50000.00"),
                "SB1000000001",
                "SB1000000002",
                "Large branch transfer"
        );

        // Attempt to self-approve
        BankingException exception = assertThrows(BankingException.class, () ->
                approvalService.approveRequest(request.getId(), maker, "Self-approval attempt")
        );

        assertTrue(exception.getMessage().contains("Maker cannot act as Checker"));
    }

    @Test
    @DisplayName("Maker-Checker: Distinct checker can approve and complete request")
    void testDistinctCheckerCanApprove() {
        User maker = userRepository.findByUsername("teller_a").orElseThrow();
        User checker = userRepository.findByUsername("manager_a").orElseThrow();

        ApprovalRequest request = approvalService.submitForApproval(
                null,
                maker,
                ApprovalType.HIGH_VALUE_TRANSACTION,
                new BigDecimal("30000.00"),
                "SB1000000001",
                null,
                "High value branch withdrawal"
        );

        ApprovalRequest approved = approvalService.approveRequest(request.getId(), checker, "Verified by Manager");
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        assertEquals(checker.getId(), approved.getChecker().getId());
    }

    // ==========================================
    // 5. DOUBLE-ENTRY LEDGER BOOKKEEPING
    // ==========================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Double-Entry Ledger: All deposits and withdrawals balance perfectly")
    void testDoubleEntryLedgerIntegrity() {
        Customer customerA = customerRepository.findByUserUsername("customer_a").orElseThrow();
        BigDecimal initialBal = customerA.getBalance();

        // Perform Deposit
        customerService.deposit(customerA.getAccountNumber(), new BigDecimal("500.00"), "Ledger Test Deposit");

        // Verify ledger integrity across all transactions
        boolean balanced = ledgerService.verifyLedgerIntegrity();
        assertTrue(balanced, "Total Debits must equal Total Credits across all transactions");
    }

    // ==========================================
    // 6. TRANSACTION LIMITS
    // ==========================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Transaction Limits: Single transaction exceeding limit (₹25,000) is rejected")
    void testSingleTransactionLimitExceeded() {
        Customer customerA = customerRepository.findByUserUsername("customer_a").orElseThrow();

        // Customer limit is 25,000; attempting 30,000
        BankingException exception = assertThrows(BankingException.class, () ->
                customerService.withdraw(customerA.getAccountNumber(), new BigDecimal("30000.00"), "Excessive withdrawal")
        );

        assertTrue(exception.getMessage().contains("exceeds single transaction limit"));
    }

    // ==========================================
    // 7. PUBLIC REGISTRATION & CUSTOMER ONBOARDING
    // ==========================================

    @Test
    @DisplayName("Registration: Happy path creates pending customer with ₹0 balance and dormant account")
    void testRegistrationHappyPath() throws Exception {
        String testUser = "new_customer_" + System.currentTimeMillis();
        String testEmail = testUser + "@example.com";

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", testUser)
                        .param("password", "StrongPass@123")
                        .param("confirmPassword", "StrongPass@123")
                        .param("fullName", "John Registered Doe")
                        .param("email", testEmail)
                        .param("phone", "9876543210")
                        .param("address", "10 Downing St")
                        .param("agreeTerms", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?registered=true*"));

        User createdUser = userRepository.findByUsername(testUser).orElseThrow();
        assertEquals(Role.CUSTOMER, createdUser.getRole());

        Customer createdCustomer = customerRepository.findByUserUsername(testUser).orElseThrow();
        assertEquals(CustomerStatus.PENDING_VERIFICATION, createdCustomer.getStatus());
        assertEquals(KycStatus.PENDING, createdCustomer.getKycStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(createdCustomer.getBalance()), "Initial balance must be zero");

        Account createdAccount = accountRepository.findByAccountNumber(createdCustomer.getAccountNumber()).orElseThrow();
        assertEquals(AccountStatus.DORMANT, createdAccount.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(createdAccount.getBalance()), "Account balance must be zero");
    }

    @Test
    @DisplayName("Registration: Mismatched confirm password is rejected")
    void testRegistrationMismatchedPasswordRejected() throws Exception {
        String testUser = "mismatch_" + System.currentTimeMillis();

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", testUser)
                        .param("password", "StrongPass@123")
                        .param("confirmPassword", "DifferentPass@123")
                        .param("fullName", "Mismatch User")
                        .param("email", testUser + "@example.com")
                        .param("phone", "9876543210")
                        .param("agreeTerms", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("errorMessage"));

        assertFalse(userRepository.existsByUsername(testUser));
    }

    @Test
    @DisplayName("Registration: Password shorter than 8 characters is rejected")
    void testRegistrationShortPasswordRejected() throws Exception {
        String testUser = "short_" + System.currentTimeMillis();

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", testUser)
                        .param("password", "short")
                        .param("confirmPassword", "short")
                        .param("fullName", "Short Pass User")
                        .param("email", testUser + "@example.com")
                        .param("phone", "9876543210")
                        .param("agreeTerms", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("errorMessage"));

        assertFalse(userRepository.existsByUsername(testUser));
    }

    @Test
    @DisplayName("Registration: Missing terms agreement is rejected")
    void testRegistrationMissingTermsRejected() throws Exception {
        String testUser = "noterms_" + System.currentTimeMillis();

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", testUser)
                        .param("password", "StrongPass@123")
                        .param("confirmPassword", "StrongPass@123")
                        .param("fullName", "No Terms User")
                        .param("email", testUser + "@example.com")
                        .param("phone", "9876543210")
                        .param("agreeTerms", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("errorMessage"));

        assertFalse(userRepository.existsByUsername(testUser));
    }

    @Test
    @DisplayName("Registration: Duplicate username is rejected")
    void testRegistrationDuplicateUsernameRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "customer_a")
                        .param("password", "StrongPass@123")
                        .param("confirmPassword", "StrongPass@123")
                        .param("fullName", "Duplicate A")
                        .param("email", "another_customer_a@example.com")
                        .param("phone", "9876543210")
                        .param("agreeTerms", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("Registration: Duplicate email is rejected")
    void testRegistrationDuplicateEmailRejected() throws Exception {
        Customer existing = customerRepository.findByUserUsername("customer_a").orElseThrow();

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "unique_name_" + System.currentTimeMillis())
                        .param("password", "StrongPass@123")
                        .param("confirmPassword", "StrongPass@123")
                        .param("fullName", "Duplicate Email")
                        .param("email", existing.getEmail())
                        .param("phone", "9876543210")
                        .param("agreeTerms", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    // ==========================================
    // 8. CUSTOMER PORTAL & USER MENU ROUTES
    // ==========================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Customer Portal: Logged-in customer can access profile, accounts, transactions, security")
    void testCustomerPortalUserMenuRoutes() throws Exception {
        mockMvc.perform(get("/customer/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/profile"))
                .andExpect(model().attributeExists("customer", "user"));

        mockMvc.perform(get("/customer/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/accounts"))
                .andExpect(model().attributeExists("accounts", "totalBalance"));

        mockMvc.perform(get("/customer/transactions"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/transactions"))
                .andExpect(model().attributeExists("transactions"));

        mockMvc.perform(get("/customer/security"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/security"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Customer Security: Change password with invalid current password fails")
    void testCustomerChangePasswordWrongCurrent() throws Exception {
        mockMvc.perform(post("/customer/security/change-password")
                        .with(csrf())
                        .param("currentPassword", "WrongCurrentPass")
                        .param("newPassword", "NewStrong@123")
                        .param("confirmNewPassword", "NewStrong@123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/security"));
    }

    @Test
    @WithMockUser(username = "customer_b", roles = {"CUSTOMER"})
    @DisplayName("Customer Security: Change password with valid current password succeeds")
    void testCustomerChangePasswordSuccess() throws Exception {
        mockMvc.perform(post("/customer/security/change-password")
                        .with(csrf())
                        .param("currentPassword", "Customer@123")
                        .param("newPassword", "UpdatedPass@123")
                        .param("confirmNewPassword", "UpdatedPass@123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/security"));

        User customerB = userRepository.findByUsername("customer_b").orElseThrow();
        assertTrue(passwordEncoder.matches("UpdatedPass@123", customerB.getPassword()));
    }

    // ==========================================
    // 9. KYC SUBMISSION & VERIFICATION WORKFLOW
    // ==========================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("KYC: Customer can submit KYC identity document")
    void testCustomerKycSubmission() throws Exception {
        mockMvc.perform(post("/customer/kyc")
                        .with(csrf())
                        .param("documentType", "PASSPORT")
                        .param("documentNumber", "PASS-998877"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/profile"));

        Customer customerA = customerRepository.findByUserUsername("customer_a").orElseThrow();
        List<KycDocument> docs = kycDocumentRepository.findByCustomerId(customerA.getId());
        assertFalse(docs.isEmpty());
        assertTrue(docs.stream().anyMatch(d -> "PASS-998877".equals(d.getDocumentNumber())));
    }

    @Test
    @WithMockUser(username = "teller_a", roles = {"CUSTOMER_SERVICE_OFFICER"})
    @DisplayName("KYC: CSO officer can verify KYC and activate pending customer")
    void testCsoVerifyKycWorkflow() throws Exception {
        // Create a pending customer
        String username = "kyc_target_" + System.currentTimeMillis();
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode("Pass@12345"))
                .email(username + "@example.com")
                .fullName("KYC Target")
                .role(Role.CUSTOMER)
                .enabled(true)
                .accountNonLocked(true)
                .build();
        User savedUser = userRepository.save(user);

        Customer customer = Customer.builder()
                .name("KYC Target")
                .email(username + "@example.com")
                .phone("9876543210")
                .user(savedUser)
                .balance(BigDecimal.ZERO)
                .kycStatus(KycStatus.PENDING)
                .status(CustomerStatus.PENDING_VERIFICATION)
                .build();
        Customer savedCustomer = customerService.createCustomer(customer);

        KycDocument doc = KycDocument.builder()
                .customer(savedCustomer)
                .documentType("NATIONAL_ID")
                .documentNumber("NID-123456")
                .status(KycStatus.PENDING)
                .build();
        KycDocument savedDoc = kycDocumentRepository.save(doc);

        // CSO verifies the KYC
        mockMvc.perform(post("/staff/cso/verify-kyc")
                        .with(csrf())
                        .param("documentId", savedDoc.getId().toString())
                        .param("decision", "APPROVED")
                        .param("remarks", "Approved by CSO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/cso"));

        Customer verifiedCustomer = customerRepository.findById(savedCustomer.getId()).orElseThrow();
        assertEquals(CustomerStatus.ACTIVE, verifiedCustomer.getStatus());
        assertEquals(KycStatus.VERIFIED, verifiedCustomer.getKycStatus());

        Account activatedAccount = accountRepository.findByAccountNumber(verifiedCustomer.getAccountNumber()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, activatedAccount.getStatus());
    }

    // ==========================================
    // 12. FINANCIAL AUDIT & DASHBOARD AGGREGATION TESTS
    // ==========================================

    @Test
    @DisplayName("Financial Audit: Transaction aggregation strictly filters by SUCCESS and excludes non-completed statuses")
    void testTransactionAggregationExcludesNonSuccess() {
        Customer testCustomer = customerRepository.findAll().get(0);

        long baseCount = transactionRepository.countTotalTransactions();
        BigDecimal baseDeposits = transactionRepository.sumTotalDeposits();
        BigDecimal baseWithdrawals = transactionRepository.sumTotalWithdrawals();

        // Create FAILED deposit
        Transaction failedDeposit = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("9999.00"))
                .receiverAccount(testCustomer)
                .status(Transaction.TransactionStatus.FAILED)
                .description("Failed deposit test")
                .build();
        transactionRepository.save(failedDeposit);

        // Create PENDING_APPROVAL deposit
        Transaction pendingDeposit = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("8888.00"))
                .receiverAccount(testCustomer)
                .status(Transaction.TransactionStatus.PENDING_APPROVAL)
                .description("Pending deposit test")
                .build();
        transactionRepository.save(pendingDeposit);

        // Create REJECTED withdrawal
        Transaction rejectedWithdrawal = Transaction.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("7777.00"))
                .senderAccount(testCustomer)
                .status(Transaction.TransactionStatus.REJECTED)
                .description("Rejected withdrawal test")
                .build();
        transactionRepository.save(rejectedWithdrawal);

        // Assert counts and sums did NOT change
        assertEquals(baseCount, transactionRepository.countTotalTransactions(), "Non-SUCCESS transactions must not be counted");
        assertEquals(0, baseDeposits.compareTo(transactionRepository.sumTotalDeposits()), "Non-SUCCESS deposits must not be summed");
        assertEquals(0, baseWithdrawals.compareTo(transactionRepository.sumTotalWithdrawals()), "Non-SUCCESS withdrawals must not be summed");
    }

    @Test
    @DisplayName("Financial Audit: Customer & Account aggregation excludes DELETED customers and CLOSED accounts")
    void testCustomerAndAccountAggregationExcludesDeletedAndClosed() {
        long baseCustomerCount = customerRepository.countTotalCustomers();
        BigDecimal baseBalance = customerService.sumTotalBalance();

        // Create a customer with status DELETED
        Customer deletedCust = Customer.builder()
                .name("Deleted Test Cust")
                .email("deleted_test_" + System.currentTimeMillis() + "@example.com")
                .phone("1234567890")
                .balance(new BigDecimal("50000.00"))
                .status(CustomerStatus.DELETED)
                .accountNumber("SB_DEL_" + System.currentTimeMillis())
                .build();
        customerRepository.save(deletedCust);

        // countTotalCustomers must exclude DELETED
        assertEquals(baseCustomerCount, customerRepository.countTotalCustomers());

        // Account with CLOSED status must be excluded from total balance
        Account closedAcc = Account.builder()
                .accountNumber("ACC_CLOSED_" + System.currentTimeMillis())
                .customer(deletedCust)
                .balance(new BigDecimal("50000.00"))
                .ledgerBalance(new BigDecimal("50000.00"))
                .status(AccountStatus.CLOSED)
                .accountType(AccountType.SAVINGS)
                .build();
        accountRepository.save(closedAcc);

        assertEquals(0, baseBalance.compareTo(customerService.sumTotalBalance()), "Closed accounts must not be added to total balance");
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Financial Audit & Role Isolation: Customer visiting global /dashboard is redirected to /customer/dashboard")
    void testCustomerCannotViewGlobalDashboardTotals() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/dashboard"));
    }

    @Test
    @WithMockUser(username = "admin_a", roles = {"BANK_ADMIN"})
    @DisplayName("Financial Audit: Bank Admin viewing /dashboard receives authoritative aggregate totals in model")
    void testAdminViewingDashboardReceivesAuthoritativeTotals() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("totalCustomers"))
                .andExpect(model().attributeExists("totalBalance"))
                .andExpect(model().attributeExists("totalDeposits"))
                .andExpect(model().attributeExists("totalWithdrawals"));
    }
}
