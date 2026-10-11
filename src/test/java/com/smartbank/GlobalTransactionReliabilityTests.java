package com.smartbank;

import com.smartbank.entity.*;
import com.smartbank.exception.AccountNotFoundException;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.service.CustomerService;
import com.smartbank.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class GlobalTransactionReliabilityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerService ledgerService;

    private Customer customerA;
    private Customer customerB;

    @BeforeEach
    void setUp() {
        customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));
        customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        customerA.setStatus(CustomerStatus.ACTIVE);
        customerRepository.save(customerA);
        customerB.setStatus(CustomerStatus.ACTIVE);
        customerRepository.save(customerB);

        Account accA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        accA.setStatus(AccountStatus.ACTIVE);
        accA.setBalance(new BigDecimal("10000.00"));
        accA.setLedgerBalance(new BigDecimal("10000.00"));
        accA.setDailyTransferTotal(BigDecimal.ZERO);
        accA.setDailyTransferDate(LocalDate.now());
        accountRepository.save(accA);

        Account accB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow();
        accB.setStatus(AccountStatus.ACTIVE);
        accB.setBalance(new BigDecimal("5000.00"));
        accB.setLedgerBalance(new BigDecimal("5000.00"));
        accB.setDailyTransferTotal(BigDecimal.ZERO);
        accB.setDailyTransferDate(LocalDate.now());
        accountRepository.save(accB);
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 1: Transfer from Customer A to Customer B updates balances atomically and records isolated history")
    void testTransferBetweenDifferentCustomersSuccess() {
        BigDecimal initialBalA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal initialBalB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal transferAmount = new BigDecimal("250.00");

        customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), transferAmount, "A to B test");

        Account updatedAccA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        Account updatedAccB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow();
        Customer updatedCustA = customerRepository.findById(customerA.getId()).orElseThrow();
        Customer updatedCustB = customerRepository.findById(customerB.getId()).orElseThrow();

        assertEquals(0, initialBalA.subtract(transferAmount).compareTo(updatedAccA.getBalance()), "Sender account balance must decrease");
        assertEquals(0, initialBalB.add(transferAmount).compareTo(updatedAccB.getBalance()), "Receiver account balance must increase");
        assertEquals(0, updatedAccA.getBalance().compareTo(updatedCustA.getBalance()), "Sender customer entity balance must stay synced");
        assertEquals(0, updatedAccB.getBalance().compareTo(updatedCustB.getBalance()), "Receiver customer entity balance must stay synced");

        // Verify transaction history isolation
        List<Transaction> txnsA = transactionRepository.findAllByCustomer(updatedCustA);
        List<Transaction> txnsB = transactionRepository.findAllByCustomer(updatedCustB);

        // Sender history must see TRANSFER_SENT and must not see TRANSFER_RECEIVED for this transfer
        boolean senderHasSent = txnsA.stream()
                .anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.TRANSFER_SENT &&
                        t.getDescription() != null && t.getDescription().contains("A to B test"));
        boolean senderHasReceived = txnsA.stream()
                .anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.TRANSFER_RECEIVED &&
                        t.getDescription() != null && t.getDescription().contains("A to B test"));
        assertTrue(senderHasSent, "Sender history must show TRANSFER_SENT");
        assertFalse(senderHasReceived, "Sender history must not show TRANSFER_RECEIVED");

        // Receiver history must see TRANSFER_RECEIVED and must not see TRANSFER_SENT for this transfer
        boolean receiverHasReceived = txnsB.stream()
                .anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.TRANSFER_RECEIVED &&
                        t.getDescription() != null && t.getDescription().contains("A to B test"));
        boolean receiverHasSent = txnsB.stream()
                .anyMatch(t -> t.getTransactionType() == Transaction.TransactionType.TRANSFER_SENT &&
                        t.getDescription() != null && t.getDescription().contains("A to B test"));
        assertTrue(receiverHasReceived, "Receiver history must show TRANSFER_RECEIVED");
        assertFalse(receiverHasSent, "Receiver history must not show TRANSFER_SENT");

        // Verify ledger integrity
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Double-entry ledger must remain balanced");
    }

    @Test
    @WithMockUser(username = "customer_b", roles = {"CUSTOMER"})
    @DisplayName("Reliability 2: Transfer from Customer B to Customer A succeeds in opposite direction")
    void testTransferInOppositeDirectionSuccess() {
        BigDecimal initialBalB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal initialBalA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal transferAmount = new BigDecimal("150.00");

        customerService.transfer(customerB.getAccountNumber(), customerA.getAccountNumber(), transferAmount, "B to A test");

        Account updatedAccB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow();
        Account updatedAccA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();

        assertEquals(0, initialBalB.subtract(transferAmount).compareTo(updatedAccB.getBalance()));
        assertEquals(0, initialBalA.add(transferAmount).compareTo(updatedAccA.getBalance()));
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Double-entry ledger must be balanced");
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 3: Same customer can transfer between their own distinct accounts")
    void testTransferBetweenCustomerOwnAccountsSuccess() {
        String secondaryAccNum = "SB" + customerA.getId() + "999";
        Account secondaryAccount = accountRepository.findByAccountNumber(secondaryAccNum).orElseGet(() -> {
            Account acc = Account.builder()
                    .accountNumber(secondaryAccNum)
                    .customer(customerA)
                    .branch(customerA.getBranch())
                    .accountType(AccountType.CHECKING)
                    .status(AccountStatus.ACTIVE)
                    .balance(new BigDecimal("1000.00"))
                    .ledgerBalance(new BigDecimal("1000.00"))
                    .currency("INR")
                    .build();
            return accountRepository.save(acc);
        });

        BigDecimal primaryInitial = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal secondaryInitial = secondaryAccount.getBalance();
        BigDecimal transferAmount = new BigDecimal("300.00");

        customerService.transfer(customerA.getAccountNumber(), secondaryAccNum, transferAmount, "Own account transfer");

        Account primaryUpdated = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        Account secondaryUpdated = accountRepository.findByAccountNumber(secondaryAccNum).orElseThrow();

        assertEquals(0, primaryInitial.subtract(transferAmount).compareTo(primaryUpdated.getBalance()));
        assertEquals(0, secondaryInitial.add(transferAmount).compareTo(secondaryUpdated.getBalance()));
        assertTrue(ledgerService.verifyLedgerIntegrity());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 4: Unauthorized source account access is strictly denied (403)")
    void testUnauthorizedSourceAccountRejected() {
        // Customer A trying to send from Customer B's account
        assertThrows(AccessDeniedException.class, () ->
                customerService.transfer(customerB.getAccountNumber(), customerA.getAccountNumber(), new BigDecimal("100.00"), "Illegal transfer")
        );
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 5: Nonexistent destination account produces AccountNotFoundException, not generic 500")
    void testTransferToNonexistentAccountFailsSafely() {
        assertThrows(AccountNotFoundException.class, () ->
                customerService.transfer(customerA.getAccountNumber(), "SB0000000000", new BigDecimal("100.00"), "Nonexistent target")
        );
    }

    @Test
    @WithMockUser(username = "teller_a", roles = {"TELLER"})
    @DisplayName("Reliability 6: Staff initiating transfer from nonexistent source throws AccountNotFoundException")
    void testStaffTransferFromNonexistentAccountFailsSafely() {
        assertThrows(AccountNotFoundException.class, () ->
                customerService.transfer("SB9999999999", customerB.getAccountNumber(), new BigDecimal("100.00"), "Nonexistent source")
        );
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 7: Transfer to same account is rejected with descriptive message")
    void testTransferToSameAccountRejected() {
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(customerA.getAccountNumber(), customerA.getAccountNumber(), new BigDecimal("100.00"), "Self transfer")
        );
        assertTrue(ex.getMessage().contains("Cannot transfer to the same account"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 8: Insufficient funds throws InsufficientFundsException with zero balance deduction")
    void testTransferWithInsufficientFundsRejected() {
        Account currentAcc = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        currentAcc.setBalance(new BigDecimal("500.00"));
        currentAcc.setLedgerBalance(new BigDecimal("500.00"));
        accountRepository.save(currentAcc);

        BigDecimal balanceBefore = currentAcc.getBalance();
        BigDecimal excessiveAmount = new BigDecimal("1500.00"); // 1500 < limit (25000), but > balance (500)

        assertThrows(InsufficientFundsException.class, () ->
                customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), excessiveAmount, "Overdraft attempt")
        );

        Account afterAcc = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        assertEquals(0, balanceBefore.compareTo(afterAcc.getBalance()), "Balance must remain unchanged after failed transfer");
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 9: Transfer exceeding single transaction limit is rejected cleanly")
    void testTransferExceedingSingleTransactionLimitRejected() {
        // Customer single transaction limit is configured to 25,000.00
        BigDecimal overLimit = new BigDecimal("30000.00");

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), overLimit, "Over limit transfer")
        );
        assertTrue(ex.getMessage().contains("single transaction limit"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 10: Cumulative transfers exceeding daily limit are rejected cleanly")
    void testTransferExceedingDailyLimitRejected() {
        // Daily limit for customer is configured to 50,000.00
        String dailyAccNum = "SBDAILY" + customerA.getId();
        Account dailyAcc = accountRepository.findByAccountNumber(dailyAccNum).orElseGet(() -> {
            return accountRepository.save(Account.builder()
                    .accountNumber(dailyAccNum)
                    .customer(customerA)
                    .branch(customerA.getBranch())
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .balance(new BigDecimal("50000.00"))
                    .ledgerBalance(new BigDecimal("50000.00"))
                    .currency("INR")
                    .build());
        });
        dailyAcc.setDailyTransferDate(LocalDate.now());
        dailyAcc.setDailyTransferTotal(new BigDecimal("45000.00"));
        accountRepository.save(dailyAcc);

        // Attempt transfer of 10,000.00 (projected daily: 55,000.00 > 50,000.00)
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(dailyAccNum, customerB.getAccountNumber(), new BigDecimal("10000.00"), "Daily limit test")
        );
        assertTrue(ex.getMessage().contains("Daily limit"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 11: Rapid duplicate transfer debounce blocks accidental double-submissions")
    void testRapidDuplicateTransferDebounceBlocked() {
        BigDecimal amount = new BigDecimal("75.00");
        customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), amount, "Initial transfer");

        // Immediate duplicate transfer of same amount to same receiver
        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), amount, "Immediate duplicate")
        );
        assertTrue(ex.getMessage().contains("duplicate transfer") || ex.getMessage().contains("wait a few moments"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 12: Frozen sender account blocks transfers cleanly")
    void testFrozenSenderAccountBlocksTransfers() {
        String frozenAccNum = "SBFROZEN" + customerA.getId();
        Account frozenAcc = accountRepository.findByAccountNumber(frozenAccNum).orElseGet(() -> {
            return accountRepository.save(Account.builder()
                    .accountNumber(frozenAccNum)
                    .customer(customerA)
                    .branch(customerA.getBranch())
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.FROZEN)
                    .balance(new BigDecimal("1000.00"))
                    .ledgerBalance(new BigDecimal("1000.00"))
                    .currency("INR")
                    .build());
        });
        frozenAcc.setStatus(AccountStatus.FROZEN);
        accountRepository.save(frozenAcc);

        BankingException ex = assertThrows(BankingException.class, () ->
                customerService.transfer(frozenAccNum, customerB.getAccountNumber(), new BigDecimal("10.00"), "Frozen sender test")
        );
        assertTrue(ex.getMessage().contains("frozen"));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 13: MVC form transfer handles BankingException without triggering generic 500 error page")
    void testMvcFormTransferHandlesBusinessExceptionCleanly() throws Exception {
        mockMvc.perform(post("/transfer")
                        .with(csrf())
                        .param("senderAccount", customerA.getAccountNumber())
                        .param("receiverAccount", customerA.getAccountNumber()) // Same account error
                        .param("amount", "100.00")
                        .param("description", "MVC test"))
                .andExpect(status().isOk())
                .andExpect(view().name("transfer"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("Cannot transfer to the same account")));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 14: Customer portal form transfer handles business exceptions gracefully")
    void testCustomerPortalTransferHandlesBusinessExceptionCleanly() throws Exception {
        mockMvc.perform(post("/customer/transfer")
                        .with(csrf())
                        .param("senderAccountNumber", customerA.getAccountNumber())
                        .param("receiverAccountNumber", "SB0000000000") // Non-existent account
                        .param("amount", "50.00")
                        .param("description", "Portal invalid target test"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/transfer"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("Account not found")));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Reliability 15: Deposits and Withdrawals remain fully operational with double-entry ledgers")
    void testDepositAndWithdrawalRemainReliable() {
        BigDecimal initialBal = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal depAmount = new BigDecimal("200.00");
        BigDecimal withAmount = new BigDecimal("100.00");

        customerService.deposit(customerA.getAccountNumber(), depAmount, "Test deposit");
        Account afterDep = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        assertEquals(0, initialBal.add(depAmount).compareTo(afterDep.getBalance()));

        customerService.withdraw(customerA.getAccountNumber(), withAmount, "Test withdraw");
        Account afterWith = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        assertEquals(0, initialBal.add(depAmount).subtract(withAmount).compareTo(afterWith.getBalance()));

        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger entries must be balanced");
    }

    @Test
    @DisplayName("Reliability 16: Concurrent bidirectional transfers execute without deadlocks and preserve balance conservation")
    void testConcurrentBidirectionalTransfersPreserveBalancesWithoutDeadlock() throws Exception {
        BigDecimal initialBalA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal initialBalB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal totalInitial = initialBalA.add(initialBalB);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);

        // Thread 1: Customer A transfers 200 to Customer B
        executor.submit(() -> {
            try {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("customer_a", "password", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                );
                startLatch.await();
                customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), new BigDecimal("200.00"), "Concurrent A to B");
                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Customer B transfers 100 to Customer A
        executor.submit(() -> {
            try {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("customer_b", "password", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                );
                startLatch.await();
                customerService.transfer(customerB.getAccountNumber(), customerA.getAccountNumber(), new BigDecimal("100.00"), "Concurrent B to A");
                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Transfers should finish within 10 seconds without deadlocking");
        assertEquals(2, successCount.get(), "Both concurrent transfers should succeed");

        Account finalAccA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        Account finalAccB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow();
        BigDecimal totalFinal = finalAccA.getBalance().add(finalAccB.getBalance());

        assertEquals(0, totalInitial.compareTo(totalFinal), "Total balance between accounts must be conserved exactly");
        assertEquals(0, initialBalA.subtract(new BigDecimal("100.00")).compareTo(finalAccA.getBalance()));
        assertEquals(0, initialBalB.add(new BigDecimal("100.00")).compareTo(finalAccB.getBalance()));
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger entries must be balanced");
    }

    @Test
    @DisplayName("Reliability 17: Concurrent identical duplicate submissions serialize cleanly and reject duplicate execution")
    void testConcurrentDuplicateSubmissionDebounceSafety() throws Exception {
        BigDecimal initialBalA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow().getBalance();
        BigDecimal initialBalB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow().getBalance();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateBlockedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken("customer_a", "password", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    );
                    startLatch.await();
                    customerService.transfer(customerA.getAccountNumber(), customerB.getAccountNumber(), new BigDecimal("150.00"), "Simultaneous duplicate test");
                    successCount.incrementAndGet();
                } catch (BankingException e) {
                    if (e.getMessage().contains("duplicate transfer")) {
                        duplicateBlockedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Transfers should finish within 10 seconds");
        assertEquals(1, successCount.get(), "Exactly one duplicate transfer should succeed");
        assertEquals(1, duplicateBlockedCount.get(), "The duplicate transfer should be blocked by the debounce check");

        Account finalAccA = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        Account finalAccB = accountRepository.findByAccountNumber(customerB.getAccountNumber()).orElseThrow();

        // Exactly one transfer of 150 occurred
        assertEquals(0, initialBalA.subtract(new BigDecimal("150.00")).compareTo(finalAccA.getBalance()));
        assertEquals(0, initialBalB.add(new BigDecimal("150.00")).compareTo(finalAccB.getBalance()));
        assertTrue(ledgerService.verifyLedgerIntegrity(), "Ledger entries must remain balanced");
    }
}
