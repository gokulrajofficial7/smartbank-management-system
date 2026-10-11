package com.smartbank;

import com.smartbank.entity.*;
import com.smartbank.exception.AccountNotFoundException;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.repository.*;
import com.smartbank.service.ApprovalService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class VishvaShivaTransferIntegrationTests {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private LedgerService ledgerService;

    private static final String VISHVA_ACC = "SB6609250805";
    private static final String SHIVA_ACC = "SB9498071821";

    private Customer vishvaCustomer;
    private Customer shivaCustomer;
    private Account vishvaAccount;
    private Account shivaAccount;
    private User managerUser;

    @BeforeEach
    void setUp() {
        // Setup Vishva User & Customer
        User userVishva = userRepository.findByUsername("customer_vishva").orElseGet(() -> {
            return userRepository.save(User.builder()
                    .username("customer_vishva")
                    .password("$2a$12$e80yqXjE1r9u6o0s5g4m2e6N.g6m.3mZq9X2m7q6X8e2m1e4r3y7u")
                    .email("vishva@example.com")
                    .fullName("VISHVA V")
                    .role(Role.CUSTOMER)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build());
        });

        vishvaCustomer = customerRepository.findByAccountNumber(VISHVA_ACC).orElseGet(() -> {
            return customerRepository.save(Customer.builder()
                    .name("VISHVA V")
                    .email("vishva@example.com")
                    .phone("9876543299")
                    .accountNumber(VISHVA_ACC)
                    .balance(new BigDecimal("50000.00"))
                    .user(userVishva)
                    .status(CustomerStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .build());
        });
        vishvaCustomer.setBalance(new BigDecimal("50000.00"));
        vishvaCustomer.setStatus(CustomerStatus.ACTIVE);
        customerRepository.save(vishvaCustomer);

        vishvaAccount = accountRepository.findByAccountNumber(VISHVA_ACC).orElseGet(() -> {
            return accountRepository.save(Account.builder()
                    .accountNumber(VISHVA_ACC)
                    .customer(vishvaCustomer)
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .balance(new BigDecimal("50000.00"))
                    .ledgerBalance(new BigDecimal("50000.00"))
                    .currency("INR")
                    .build());
        });
        vishvaAccount.setBalance(new BigDecimal("50000.00"));
        vishvaAccount.setLedgerBalance(new BigDecimal("50000.00"));
        vishvaAccount.setStatus(AccountStatus.ACTIVE);
        vishvaAccount.setDailyTransferTotal(BigDecimal.ZERO);
        vishvaAccount.setDailyTransferDate(LocalDate.now());
        accountRepository.save(vishvaAccount);

        // Setup Shiva User & Customer
        User userShiva = userRepository.findByUsername("customer_shiva").orElseGet(() -> {
            return userRepository.save(User.builder()
                    .username("customer_shiva")
                    .password("$2a$12$e80yqXjE1r9u6o0s5g4m2e6N.g6m.3mZq9X2m7q6X8e2m1e4r3y7u")
                    .email("shiva@example.com")
                    .fullName("SHIVAKUMAR S")
                    .role(Role.CUSTOMER)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build());
        });

        shivaCustomer = customerRepository.findByAccountNumber(SHIVA_ACC).orElseGet(() -> {
            return customerRepository.save(Customer.builder()
                    .name("SHIVAKUMAR S")
                    .email("shiva@example.com")
                    .phone("9876543298")
                    .accountNumber(SHIVA_ACC)
                    .balance(new BigDecimal("160000.00"))
                    .user(userShiva)
                    .status(CustomerStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .build());
        });
        shivaCustomer.setBalance(new BigDecimal("160000.00"));
        shivaCustomer.setStatus(CustomerStatus.ACTIVE);
        customerRepository.save(shivaCustomer);

        shivaAccount = accountRepository.findByAccountNumber(SHIVA_ACC).orElseGet(() -> {
            return accountRepository.save(Account.builder()
                    .accountNumber(SHIVA_ACC)
                    .customer(shivaCustomer)
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .balance(new BigDecimal("160000.00"))
                    .ledgerBalance(new BigDecimal("160000.00"))
                    .currency("INR")
                    .build());
        });
        shivaAccount.setBalance(new BigDecimal("160000.00"));
        shivaAccount.setLedgerBalance(new BigDecimal("160000.00"));
        shivaAccount.setStatus(AccountStatus.ACTIVE);
        shivaAccount.setDailyTransferTotal(BigDecimal.ZERO);
        shivaAccount.setDailyTransferDate(LocalDate.now());
        accountRepository.save(shivaAccount);

        // Setup Manager User
        managerUser = userRepository.findByUsername("manager_a").orElseGet(() -> {
            return userRepository.save(User.builder()
                    .username("manager_a")
                    .password("$2a$12$e80yqXjE1r9u6o0s5g4m2e6N.g6m.3mZq9X2m7q6X8e2m1e4r3y7u")
                    .email("manager@example.com")
                    .fullName("Branch Manager")
                    .role(Role.BRANCH_MANAGER)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build());
        });
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 2, 3, 4: Direct transfer under approval threshold (₹5,000) from Vishva to Shiva updates balances, history, and ledger")
    void testDirectTransferUnderThresholdFromVishvaToShiva() {
        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaBefore = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("50000.00").compareTo(vishvaBefore));
        assertEquals(0, new BigDecimal("160000.00").compareTo(shivaBefore));

        BigDecimal transferAmount = new BigDecimal("5000.00");
        customerService.transfer(VISHVA_ACC, SHIVA_ACC, transferAmount, "Direct transfer under approval threshold");

        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfter = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        // Exact ₹5,000.00 debit and credit verified
        assertEquals(0, new BigDecimal("45000.00").compareTo(vishvaAfter), "Vishva balance must be debited exactly ₹5,000");
        assertEquals(0, new BigDecimal("165000.00").compareTo(shivaAfter), "Shiva balance must be credited exactly ₹5,000");

        // Transaction history verified
        List<Transaction> vishvaTxns = transactionRepository.findAllByAccountNumber(VISHVA_ACC);
        assertFalse(vishvaTxns.isEmpty());
        Transaction sentTxn = vishvaTxns.get(0);
        assertEquals(Transaction.TransactionType.TRANSFER_SENT, sentTxn.getTransactionType());
        assertEquals(0, transferAmount.compareTo(sentTxn.getAmount()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(sentTxn.getBalanceAfterTransaction()));

        List<Transaction> shivaTxns = transactionRepository.findAllByAccountNumber(SHIVA_ACC);
        assertFalse(shivaTxns.isEmpty());
        Transaction receivedTxn = shivaTxns.get(0);
        assertEquals(Transaction.TransactionType.TRANSFER_RECEIVED, receivedTxn.getTransactionType());
        assertEquals(0, transferAmount.compareTo(receivedTxn.getAmount()));
        assertEquals(0, new BigDecimal("165000.00").compareTo(receivedTxn.getBalanceAfterTransaction()));

        // Double-entry ledger integrity verified
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger debits and credits must be balanced");
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 2, 3, 4: Transfer of ₹10,000 triggers Dual-Control Manager Approval, then upon approval debits and credits exact ₹10,000")
    void testTransferOfTenThousandTriggersDualControlAndWhenApprovedDebitsCreditsExactTenThousand() {
        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaBefore = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();
        BigDecimal totalInitial = vishvaBefore.add(shivaBefore);

        BigDecimal transferAmount = new BigDecimal("10000.00");

        // 1. Initial attempt: Triggers Dual-Control Maker-Checker escalation
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, transferAmount, "₹10,000 transfer from Vishva to Shiva")
        );
        assertTrue(ex.getMessage().contains("Submitted for Dual-Control Manager Approval") || ex.getMessage().contains("Manager Approval"));

        // Confirm neither balance changed during pending approval
        BigDecimal vishvaDuringPending = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaDuringPending = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.compareTo(vishvaDuringPending), "Vishva balance must not change while pending approval");
        assertEquals(0, shivaBefore.compareTo(shivaDuringPending), "Shiva balance must not change while pending approval");

        // 2. Fetch the pending approval request
        List<ApprovalRequest> pendingRequests = approvalRequestRepository.findByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING);
        assertFalse(pendingRequests.isEmpty(), "Approval request must be in PENDING state");
        ApprovalRequest pending = pendingRequests.get(pendingRequests.size() - 1);
        assertEquals(0, transferAmount.compareTo(pending.getAmount()));
        assertEquals(VISHVA_ACC, pending.getSourceAccountNumber());
        assertEquals(SHIVA_ACC, pending.getTargetAccountNumber());

        // 3. Manager Checker approves the request
        approvalService.approveRequest(pending.getId(), managerUser, "Approved by Branch Manager A");

        // 4. Verify post-approval balances: Exact ₹10,000.00 debit and credit
        BigDecimal vishvaAfterApproval = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfterApproval = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        assertEquals(0, new BigDecimal("40000.00").compareTo(vishvaAfterApproval), "Vishva must be debited exactly ₹10,000 upon approval");
        assertEquals(0, new BigDecimal("170000.00").compareTo(shivaAfterApproval), "Shiva must be credited exactly ₹10,000 upon approval");
        assertEquals(0, totalInitial.compareTo(vishvaAfterApproval.add(shivaAfterApproval)), "Total funds must be conserved exactly");

        // Double-entry ledger verified
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger entries must be balanced");
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("DORMANT sender status alone does not prevent eligible internal transfer")
    void testDormantSenderCanPerformInternalTransfer() {
        vishvaAccount.setStatus(AccountStatus.DORMANT);
        accountRepository.save(vishvaAccount);

        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaBefore = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        BigDecimal transferAmount = new BigDecimal("4000.00");
        customerService.transfer(VISHVA_ACC, SHIVA_ACC, transferAmount, "Dormant sender eligible transfer");

        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfter = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        assertEquals(0, vishvaBefore.subtract(transferAmount).compareTo(vishvaAfter), "Vishva balance must be debited ₹4,000");
        assertEquals(0, shivaBefore.add(transferAmount).compareTo(shivaAfter), "Shiva balance must be credited ₹4,000");
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger entries must be balanced");
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("FROZEN sender account is strictly rejected and balances remain unchanged")
    void testFrozenSenderAccountRejectedBalancesUnchanged() {
        vishvaAccount.setStatus(AccountStatus.FROZEN);
        accountRepository.save(vishvaAccount);

        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaBefore = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("3500.00"), "Frozen attempt")
        );
        assertTrue(ex.getMessage().toLowerCase().contains("frozen"));

        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfter = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        assertEquals(0, vishvaBefore.compareTo(vishvaAfter), "Vishva balance must remain unchanged on rejection");
        assertEquals(0, shivaBefore.compareTo(shivaAfter), "Shiva balance must remain unchanged on rejection");
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("CLOSED sender account is strictly rejected and balances remain unchanged")
    void testClosedSenderAccountRejectedBalancesUnchanged() {
        vishvaAccount.setStatus(AccountStatus.CLOSED);
        accountRepository.save(vishvaAccount);

        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaBefore = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("3600.00"), "Closed attempt")
        );
        assertTrue(ex.getMessage().toLowerCase().contains("closed"));

        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfter = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        assertEquals(0, vishvaBefore.compareTo(vishvaAfter), "Vishva balance must remain unchanged on rejection");
        assertEquals(0, shivaBefore.compareTo(shivaAfter), "Shiva balance must remain unchanged on rejection");
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 4: Duplicate transfer submission is rejected without duplicate financial effect")
    void testDuplicateTransferBlockedNoDuplicateFinancialEffect() {
        customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("6000.00"), "Initial transfer");

        BigDecimal vishvaAfterFirst = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfterFirst = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("44000.00").compareTo(vishvaAfterFirst));
        assertEquals(0, new BigDecimal("166000.00").compareTo(shivaAfterFirst));

        // Attempt immediate identical duplicate transfer
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("6000.00"), "Duplicate submission")
        );
        assertTrue(ex.getMessage().contains("duplicate transfer"));

        // Confirm balances remain strictly unchanged after rejected duplicate
        BigDecimal vishvaAfterDuplicate = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfterDuplicate = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaAfterFirst.compareTo(vishvaAfterDuplicate), "Balance must not change after duplicate rejection");
        assertEquals(0, shivaAfterFirst.compareTo(shivaAfterDuplicate), "Balance must not change after duplicate rejection");
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 5: Insufficient balance transfer is rejected and balances remain unchanged")
    void testInsufficientBalanceTransferLeavesBalancesUnchanged() {
        // Set balance to ₹5,000 and attempt transfer of ₹8,000 (within single limit of ₹25k and threshold)
        vishvaAccount.setBalance(new BigDecimal("5000.00"));
        accountRepository.save(vishvaAccount);
        vishvaCustomer.setBalance(new BigDecimal("5000.00"));
        customerRepository.save(vishvaCustomer);

        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaBefore = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();

        assertThrows(InsufficientFundsException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("8000.00"), "Exceeding balance")
        );

        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BigDecimal shivaAfter = accountRepository.findByAccountNumber(SHIVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.compareTo(vishvaAfter));
        assertEquals(0, shivaBefore.compareTo(shivaAfter));
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 5: Invalid zero and negative amounts rejected cleanly")
    void testInvalidAmountsRejected() {
        assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, BigDecimal.ZERO, "Zero amount")
        );
        assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("-500.00"), "Negative amount")
        );
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 5: Valid deposit and withdrawal execute cleanly with balanced ledger")
    void testDepositAndWithdrawalOnVishvaAccount() {
        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();

        // Deposit ₹5,000.00
        customerService.deposit(VISHVA_ACC, new BigDecimal("5000.00"), "Cash deposit");
        BigDecimal vishvaAfterDep = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.add(new BigDecimal("5000.00")).compareTo(vishvaAfterDep));

        // Withdraw ₹2,000.00
        customerService.withdraw(VISHVA_ACC, new BigDecimal("2000.00"), "ATM withdrawal");
        BigDecimal vishvaAfterWith = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.add(new BigDecimal("3000.00")).compareTo(vishvaAfterWith));

        assertTrue(ledgerService.verifyLedgerIntegrity());
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 5: Withdrawal exceeding balance is rejected cleanly")
    void testWithdrawalExceedingBalanceRejected() {
        // Set balance to ₹3,000 and attempt withdrawal of ₹5,000 (within ₹25k limit)
        vishvaAccount.setBalance(new BigDecimal("3000.00"));
        accountRepository.save(vishvaAccount);
        vishvaCustomer.setBalance(new BigDecimal("3000.00"));
        customerRepository.save(vishvaCustomer);

        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertThrows(InsufficientFundsException.class, () ->
                customerService.withdraw(VISHVA_ACC, new BigDecimal("5000.00"), "Excess withdrawal")
        );
        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.compareTo(vishvaAfter));
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 5: Single transaction limit of ₹25,000 enforced for Customer")
    void testSingleTransactionLimitEnforced() {
        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("26000.00"), "Exceeds 25k limit")
        );
        assertTrue(ex.getMessage().contains("single transaction limit"));
        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.compareTo(vishvaAfter));
    }

    @Test
    @WithMockUser(username = "customer_b", roles = {"CUSTOMER"})
    @DisplayName("Task 6: Unauthorized source-account access by another customer throws AccessDeniedException")
    void testUnauthorizedSourceAccountAccessRejected() {
        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertThrows(AccessDeniedException.class, () ->
                customerService.transfer(VISHVA_ACC, SHIVA_ACC, new BigDecimal("1000.00"), "IDOR attack attempt")
        );
        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.compareTo(vishvaAfter));
    }

    @Test
    @WithMockUser(username = "customer_vishva", roles = {"CUSTOMER"})
    @DisplayName("Task 6: Transfer to invalid/non-existent recipient throws AccountNotFoundException")
    void testInvalidRecipientAccountRejected() {
        BigDecimal vishvaBefore = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertThrows(AccountNotFoundException.class, () ->
                customerService.transfer(VISHVA_ACC, "SB9999999999", new BigDecimal("1000.00"), "Non-existent recipient")
        );
        BigDecimal vishvaAfter = accountRepository.findByAccountNumber(VISHVA_ACC).orElseThrow().getBalance();
        assertEquals(0, vishvaBefore.compareTo(vishvaAfter));
    }
}
