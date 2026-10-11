package com.smartbank;

import com.smartbank.entity.*;
import com.smartbank.exception.AccountNotFoundException;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.ApprovalRequestRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.CustomerService;
import com.smartbank.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GlobalTransferReliabilityRegressionTests {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private LedgerService ledgerService;

    private static final String SENDER_ACC = "SB1111222233";
    private static final String RECEIVER_ACC = "SB4444555566";
    private static final String SENDER_USERNAME = "regression_sender";
    private static final String RECEIVER_USERNAME = "regression_receiver";

    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        User senderUser = userRepository.findByUsername(SENDER_USERNAME).orElseGet(() ->
                userRepository.save(User.builder()
                        .username(SENDER_USERNAME)
                        .password("$2a$12$e80yqXjE1r9u6o0s5g4m2e6N.g6m.3mZq9X2m7q6X8e2m1e4r3y7u")
                        .email("sender@example.com")
                        .fullName("Sender Alpha")
                        .role(Role.CUSTOMER)
                        .enabled(true)
                        .accountNonLocked(true)
                        .build())
        );

        Customer senderCust = customerRepository.findByAccountNumber(SENDER_ACC).orElseGet(() ->
                customerRepository.save(Customer.builder()
                        .name("Sender Alpha")
                        .email("sender@example.com")
                        .phone("9876543210")
                        .accountNumber(SENDER_ACC)
                        .balance(new BigDecimal("50000.00"))
                        .user(senderUser)
                        .status(CustomerStatus.ACTIVE)
                        .kycStatus(KycStatus.VERIFIED)
                        .createdAt(LocalDateTime.now())
                        .build())
        );
        senderCust.setStatus(CustomerStatus.ACTIVE);
        senderCust.setBalance(new BigDecimal("50000.00"));
        customerRepository.save(senderCust);

        senderAccount = accountRepository.findByAccountNumber(SENDER_ACC).orElseGet(() ->
                accountRepository.save(Account.builder()
                        .accountNumber(SENDER_ACC)
                        .customer(senderCust)
                        .accountType(AccountType.SAVINGS)
                        .status(AccountStatus.ACTIVE)
                        .balance(new BigDecimal("50000.00"))
                        .ledgerBalance(new BigDecimal("50000.00"))
                        .currency("INR")
                        .build())
        );
        senderAccount.setStatus(AccountStatus.ACTIVE);
        senderAccount.setBalance(new BigDecimal("50000.00"));
        senderAccount.setLedgerBalance(new BigDecimal("50000.00"));
        senderAccount.setDailyTransferTotal(BigDecimal.ZERO);
        senderAccount.setDailyTransferDate(LocalDate.now());
        accountRepository.save(senderAccount);

        User receiverUser = userRepository.findByUsername(RECEIVER_USERNAME).orElseGet(() ->
                userRepository.save(User.builder()
                        .username(RECEIVER_USERNAME)
                        .password("$2a$12$e80yqXjE1r9u6o0s5g4m2e6N.g6m.3mZq9X2m7q6X8e2m1e4r3y7u")
                        .email("receiver@example.com")
                        .fullName("Receiver Beta")
                        .role(Role.CUSTOMER)
                        .enabled(true)
                        .accountNonLocked(true)
                        .build())
        );

        Customer receiverCust = customerRepository.findByAccountNumber(RECEIVER_ACC).orElseGet(() ->
                customerRepository.save(Customer.builder()
                        .name("Receiver Beta")
                        .email("receiver@example.com")
                        .phone("9876543211")
                        .accountNumber(RECEIVER_ACC)
                        .balance(new BigDecimal("30000.00"))
                        .user(receiverUser)
                        .status(CustomerStatus.ACTIVE)
                        .kycStatus(KycStatus.VERIFIED)
                        .createdAt(LocalDateTime.now())
                        .build())
        );
        receiverCust.setStatus(CustomerStatus.ACTIVE);
        receiverCust.setBalance(new BigDecimal("30000.00"));
        customerRepository.save(receiverCust);

        receiverAccount = accountRepository.findByAccountNumber(RECEIVER_ACC).orElseGet(() ->
                accountRepository.save(Account.builder()
                        .accountNumber(RECEIVER_ACC)
                        .customer(receiverCust)
                        .accountType(AccountType.SAVINGS)
                        .status(AccountStatus.ACTIVE)
                        .balance(new BigDecimal("30000.00"))
                        .ledgerBalance(new BigDecimal("30000.00"))
                        .currency("INR")
                        .build())
        );
        receiverAccount.setStatus(AccountStatus.ACTIVE);
        receiverAccount.setBalance(new BigDecimal("30000.00"));
        receiverAccount.setLedgerBalance(new BigDecimal("30000.00"));
        accountRepository.save(receiverAccount);
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 1: DORMANT sender status alone permits an otherwise eligible internal transfer")
    void testDormantSenderCanTransfer() {
        senderAccount.setStatus(AccountStatus.DORMANT);
        accountRepository.save(senderAccount);

        BigDecimal transferAmount = new BigDecimal("5000.00");
        customerService.transfer(SENDER_ACC, RECEIVER_ACC, transferAmount, "Dormant sender regression test");

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        Account receiverAfter = accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow();

        assertEquals(0, new BigDecimal("45000.00").compareTo(senderAfter.getBalance()), "Sender debited exact 5k");
        assertEquals(0, new BigDecimal("35000.00").compareTo(receiverAfter.getBalance()), "Receiver credited exact 5k");
        assertEquals(AccountStatus.DORMANT, senderAfter.getStatus(), "Sender status remains DORMANT as stored");
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Double-entry ledger must be balanced");
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 2: DORMANT receiver status alone permits an otherwise eligible internal transfer credit")
    void testDormantReceiverCanReceiveTransfer() {
        receiverAccount.setStatus(AccountStatus.DORMANT);
        accountRepository.save(receiverAccount);

        BigDecimal transferAmount = new BigDecimal("3000.00");
        customerService.transfer(SENDER_ACC, RECEIVER_ACC, transferAmount, "Dormant receiver regression test");

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        Account receiverAfter = accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow();

        assertEquals(0, new BigDecimal("47000.00").compareTo(senderAfter.getBalance()));
        assertEquals(0, new BigDecimal("33000.00").compareTo(receiverAfter.getBalance()));
        assertEquals(AccountStatus.DORMANT, receiverAfter.getStatus(), "Receiver status remains DORMANT as stored");
        assertTrue(ledgerService.verifyLedgerIntegrity());
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 3: Standard ACTIVE sender and ACTIVE receiver transfers normally")
    void testActiveSenderAndReceiverTransferNormally() {
        BigDecimal transferAmount = new BigDecimal("7000.00");
        customerService.transfer(SENDER_ACC, RECEIVER_ACC, transferAmount, "Active to Active transfer");

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        Account receiverAfter = accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow();

        assertEquals(0, new BigDecimal("43000.00").compareTo(senderAfter.getBalance()));
        assertEquals(0, new BigDecimal("37000.00").compareTo(receiverAfter.getBalance()));
        assertTrue(ledgerService.verifyLedgerIntegrity());
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 4: FROZEN sender account is strictly rejected and balances remain unchanged")
    void testFrozenSenderIsRejected() {
        senderAccount.setStatus(AccountStatus.FROZEN);
        accountRepository.save(senderAccount);

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("2000.00"), "Frozen attempt")
        );
        assertTrue(ex.getMessage().contains("frozen"));

        assertEquals(0, new BigDecimal("50000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 5: CLOSED sender account is strictly rejected and balances remain unchanged")
    void testClosedSenderIsRejected() {
        senderAccount.setStatus(AccountStatus.CLOSED);
        accountRepository.save(senderAccount);

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("2000.00"), "Closed attempt")
        );
        assertTrue(ex.getMessage().contains("closed"));

        assertEquals(0, new BigDecimal("50000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow().getBalance()));
    }

    @ParameterizedTest
    @EnumSource(value = CustomerStatus.class, names = {"SUSPENDED", "BLOCKED", "DELETED"})
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 6: SUSPENDED, BLOCKED, and DELETED customer statuses are strictly rejected")
    void testProhibitedCustomerStatusesRejected(CustomerStatus prohibitedStatus) {
        Customer senderCust = customerRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        senderCust.setStatus(prohibitedStatus);
        customerRepository.save(senderCust);

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("1000.00"), "Prohibited status attempt")
        );
        assertTrue(ex.getMessage().contains("inactive, blocked, or deleted"));

        assertEquals(0, new BigDecimal("50000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 7: Insufficient balance transfer is rejected without financial mutation")
    void testInsufficientBalanceRejected() {
        senderAccount.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(senderAccount);

        assertThrows(InsufficientFundsException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("5000.00"), "Over balance")
        );
        assertEquals(0, new BigDecimal("1000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 7b: Single transaction limit violation of ₹25,000 is rejected")
    void testSingleTransactionLimitExceededRejected() {
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("30000.00"), "Over limit")
        );
        assertTrue(ex.getMessage().contains("single transaction limit"));
        assertEquals(0, new BigDecimal("50000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(accountRepository.findByAccountNumber(RECEIVER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = "unauthorized_intruder", roles = {"CUSTOMER"})
    @DisplayName("Regression 8: Unauthorized customer attempting IDOR transfer is blocked with AccessDeniedException")
    void testUnauthorizedAccountAccessRejected() {
        assertThrows(AccessDeniedException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("1000.00"), "IDOR attempt")
        );
        assertEquals(0, new BigDecimal("50000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 9: Transfer to non-existent recipient account throws AccountNotFoundException")
    void testNonExistentRecipientRejected() {
        assertThrows(AccountNotFoundException.class, () ->
                customerService.transfer(SENDER_ACC, "SB0000000000", new BigDecimal("1000.00"), "Invalid recipient")
        );
        assertEquals(0, new BigDecimal("50000.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 10: Repeated identical duplicate transfer within debounce window is blocked")
    void testDuplicateTransferDebounced() {
        customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("2500.00"), "First transfer");
        assertEquals(0, new BigDecimal("47500.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(SENDER_ACC, RECEIVER_ACC, new BigDecimal("2500.00"), "Duplicate transfer")
        );
        assertTrue(ex.getMessage().contains("duplicate transfer"));
        assertEquals(0, new BigDecimal("47500.00").compareTo(accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow().getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 11: DORMANT account permits valid deposit and credits balance cleanly")
    void testDormantAccountCanDeposit() {
        senderAccount.setStatus(AccountStatus.DORMANT);
        accountRepository.save(senderAccount);

        BigDecimal initialBalance = senderAccount.getBalance();
        BigDecimal depositAmount = new BigDecimal("4000.00");
        customerService.deposit(SENDER_ACC, depositAmount, "Dormant deposit regression");

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        assertEquals(0, initialBalance.add(depositAmount).compareTo(senderAfter.getBalance()), "Balance credited accurately");
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger remains balanced");
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 12: DORMANT account permits valid withdrawal within balance and limits")
    void testDormantAccountCanWithdraw() {
        senderAccount.setStatus(AccountStatus.DORMANT);
        accountRepository.save(senderAccount);

        BigDecimal initialBalance = senderAccount.getBalance();
        BigDecimal withdrawAmount = new BigDecimal("3000.00");
        customerService.withdraw(SENDER_ACC, withdrawAmount, "Dormant withdrawal regression");

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        assertEquals(0, initialBalance.subtract(withdrawAmount).compareTo(senderAfter.getBalance()), "Balance debited accurately");
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger remains balanced");
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 13: FROZEN account strictly blocks deposits")
    void testFrozenAccountCannotDeposit() {
        senderAccount.setStatus(AccountStatus.FROZEN);
        accountRepository.save(senderAccount);

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.deposit(SENDER_ACC, new BigDecimal("1000.00"), "Frozen deposit attempt")
        );
        assertTrue(ex.getMessage().contains("frozen"));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 14: CLOSED account strictly blocks deposits")
    void testClosedAccountCannotDeposit() {
        senderAccount.setStatus(AccountStatus.CLOSED);
        accountRepository.save(senderAccount);

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.deposit(SENDER_ACC, new BigDecimal("1000.00"), "Closed deposit attempt")
        );
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 15: Newly registered DORMANT account permits valid withdrawal within approval threshold")
    void testNewlyRegisteredDormantAccountCanWithdrawWithinThreshold() {
        senderAccount.setStatus(AccountStatus.DORMANT);
        accountRepository.save(senderAccount);

        BigDecimal initialBal = senderAccount.getBalance();
        BigDecimal withdrawAmt = new BigDecimal("5000.00");

        customerService.withdraw(SENDER_ACC, withdrawAmt, "Eligible withdrawal within threshold");

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        assertEquals(0, initialBal.subtract(withdrawAmt).compareTo(senderAfter.getBalance()), "Balance debited accurately");
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Double-entry ledger integrity preserved");
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Regression 16: Withdrawal exceeding approval threshold creates PENDING_APPROVAL transaction without altering balance")
    void testWithdrawalExceedingApprovalThresholdCreatesPendingApproval() {
        // Customer has 50,000; threshold is 10,000; single limit is 25,000. Withdrawing 25,000 triggers approval.
        BigDecimal initialBal = senderAccount.getBalance();
        BigDecimal highValueWithdrawAmt = new BigDecimal("25000.00");

        try {
            BankingException ex = assertThrows(BankingException.class, () ->
                    customerService.withdraw(SENDER_ACC, highValueWithdrawAmt, "High value cash withdrawal")
            );

            assertTrue(ex.getMessage().contains("approval") || ex.getMessage().contains("threshold"),
                    "Exception message informs user of required manager approval");

            // Verify balance was not prematurely debited
            Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
            assertEquals(0, initialBal.compareTo(senderAfter.getBalance()), "Account balance unchanged while awaiting approval");

            // Verify pending approval request exists
            List<ApprovalRequest> pending = approvalRequestRepository.findBySourceAccountNumber(SENDER_ACC);
            assertFalse(pending.isEmpty(), "Approval request created");
            ApprovalRequest req = pending.get(pending.size() - 1);
            assertEquals(ApprovalStatus.PENDING, req.getStatus());
            assertEquals(0, highValueWithdrawAmt.compareTo(req.getAmount()));
            assertNotNull(req.getTransaction());
            Transaction savedTxn = transactionRepository.findById(req.getTransaction().getId()).orElseThrow();
            assertEquals(Transaction.TransactionStatus.PENDING_APPROVAL, savedTxn.getStatus(),
                    "Transaction status is PENDING_APPROVAL without truncation");
        } finally {
            // Clean up approval request and pending transaction created in REQUIRES_NEW
            List<ApprovalRequest> toClean = approvalRequestRepository.findBySourceAccountNumber(SENDER_ACC);
            for (ApprovalRequest r : toClean) {
                approvalRequestRepository.delete(r);
                if (r.getTransaction() != null) {
                    transactionRepository.delete(r.getTransaction());
                }
            }
        }
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 17: Withdrawal with insufficient balance throws InsufficientFundsException immediately without creating approval")
    void testWithdrawalInsufficientBalanceFailsImmediately() {
        // Set balance to 5,000 and attempt to withdraw 25,000
        senderAccount.setBalance(new BigDecimal("5000.00"));
        senderAccount.setLedgerBalance(new BigDecimal("5000.00"));
        accountRepository.save(senderAccount);

        Customer cust = customerRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        cust.setBalance(new BigDecimal("5000.00"));
        customerRepository.save(cust);

        int initialApprovalCount = approvalRequestRepository.findBySourceAccountNumber(SENDER_ACC).size();

        assertThrows(InsufficientFundsException.class, () ->
                customerService.withdraw(SENDER_ACC, new BigDecimal("25000.00"), "Overdraft attempt")
        );

        // Verify no pending approval request created for unfulfillable withdrawal
        int afterApprovalCount = approvalRequestRepository.findBySourceAccountNumber(SENDER_ACC).size();
        assertEquals(initialApprovalCount, afterApprovalCount, "No approval request created when balance is insufficient");

        // Verify balance intact
        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        assertEquals(0, new BigDecimal("5000.00").compareTo(senderAfter.getBalance()));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 18: Withdrawal from FROZEN or CLOSED account is strictly rejected")
    void testWithdrawalRestrictedAccountStatusRejection() {
        senderAccount.setStatus(AccountStatus.FROZEN);
        accountRepository.save(senderAccount);

        BankingException frozenEx = assertThrows(BankingException.class, () ->
                customerService.withdraw(SENDER_ACC, new BigDecimal("1000.00"), "Frozen withdrawal")
        );
        assertTrue(frozenEx.getMessage().toLowerCase().contains("frozen"));

        senderAccount.setStatus(AccountStatus.CLOSED);
        accountRepository.save(senderAccount);

        BankingException closedEx = assertThrows(BankingException.class, () ->
                customerService.withdraw(SENDER_ACC, new BigDecimal("1000.00"), "Closed withdrawal")
        );
        assertTrue(closedEx.getMessage().toLowerCase().contains("closed"));
    }

    @Test
    @WithMockUser(username = SENDER_USERNAME, roles = {"CUSTOMER"})
    @DisplayName("Regression 19: Database/validation failure maintains rollback atomicity")
    void testWithdrawalRollbackAtomicity() {
        BigDecimal initialBal = senderAccount.getBalance();
        long initialTxnCount = transactionRepository.count();

        // Attempt invalid withdrawal: amount <= 0
        assertThrows(BankingException.class, () ->
                customerService.withdraw(SENDER_ACC, BigDecimal.ZERO, "Invalid zero withdrawal")
        );

        Account senderAfter = accountRepository.findByAccountNumber(SENDER_ACC).orElseThrow();
        assertEquals(0, initialBal.compareTo(senderAfter.getBalance()), "Balance intact");
        assertEquals(initialTxnCount, transactionRepository.count(), "No transaction saved on validation error");
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger remains balanced");
    }
}
