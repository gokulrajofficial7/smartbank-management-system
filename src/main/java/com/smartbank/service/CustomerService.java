package com.smartbank.service;

import com.smartbank.entity.*;
import com.smartbank.exception.AccountNotFoundException;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.CustomerNotFoundException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.security.AuthorizationService;
import com.smartbank.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.smartbank.dto.BranchCustomerDto;
import com.smartbank.compliance.IndianBankingComplianceService;
import com.smartbank.compliance.CompliancePaymentChannel;
import com.smartbank.compliance.TdsCalculationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuthorizationService authorizationService;
    private final LedgerService ledgerService;
    private final TransactionLimitService transactionLimitService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final FraudRiskService fraudRiskService;
    private final CurrencyUtil currencyUtil;
    private final IndianBankingComplianceService complianceService;

    @Transactional
    public Customer createCustomer(Customer customer) {
        log.info("Creating new customer with email: {}", customer.getEmail());

        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new BankingException("A customer with email '" + customer.getEmail() + "' already exists.");
        }

        String accountNumber = generateAccountNumber();
        customer.setAccountNumber(accountNumber);
        customer.setCustomerNumber("CUST-" + accountNumber);

        if (customer.getBalance() == null) {
            customer.setBalance(BigDecimal.ZERO);
        }

        if (customer.getKycStatus() == null) {
            customer.setKycStatus(KycStatus.VERIFIED);
        }

        if (customer.getStatus() == null) {
            customer.setStatus(CustomerStatus.ACTIVE);
        }

        Customer saved = customerRepository.save(customer);

        // Also create banking Account entity for comprehensive banking operations
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customer(saved)
                .branch(saved.getBranch())
                .accountType(AccountType.SAVINGS)
                .status(saved.getStatus() == CustomerStatus.PENDING_VERIFICATION ? AccountStatus.DORMANT : AccountStatus.ACTIVE)
                .balance(saved.getBalance())
                .ledgerBalance(saved.getBalance())
                .currency("INR")
                .createdAt(LocalDateTime.now())
                .build();
        accountRepository.save(account);

        User currentUser = authorizationService.getCurrentUser().orElse(null);
        auditService.logEvent(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                currentUser != null ? currentUser.getRole().name() : "SYSTEM",
                "CREATE_CUSTOMER",
                "CUSTOMER",
                saved.getId().toString(),
                "Created customer " + saved.getName() + " with account " + accountNumber,
                "SYSTEM",
                "SUCCESS"
        );

        log.info("Customer created successfully with account number: {}", accountNumber);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer currentCust = authorizationService.getCurrentCustomer().orElse(null);
            if (currentCust != null) {
                return List.of(currentCust);
            }
            return List.of();
        }
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        // Enforce customer data isolation: customers can only view their own profile!
        authorizationService.verifyCustomerAccess(id);
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Customer getCustomerByAccountNumber(String accountNumber) {
        // Enforce account ownership check
        authorizationService.verifyAccountAccess(accountNumber);
        return customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new CustomerNotFoundException("account number", accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String query) {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer currentCust = authorizationService.getCurrentCustomer().orElse(null);
            if (currentCust != null && (currentCust.getAccountNumber().contains(query) ||
                    currentCust.getName().toLowerCase().contains(query.toLowerCase()))) {
                return List.of(currentCust);
            }
            return List.of();
        }
        return customerRepository.searchCustomers(query);
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer updatedCustomer) {
        authorizationService.verifyCustomerAccess(id);
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        if (!existing.getEmail().equals(updatedCustomer.getEmail())
                && customerRepository.existsByEmail(updatedCustomer.getEmail())) {
            throw new BankingException("Email '" + updatedCustomer.getEmail() + "' is already in use.");
        }

        existing.setName(updatedCustomer.getName());
        existing.setEmail(updatedCustomer.getEmail());
        existing.setPhone(updatedCustomer.getPhone());
        if (updatedCustomer.getAddress() != null) {
            existing.setAddress(updatedCustomer.getAddress());
        }

        User currentUser = authorizationService.getCurrentUser().orElse(null);
        auditService.logEvent(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                currentUser != null ? currentUser.getRole().name() : "SYSTEM",
                "UPDATE_CUSTOMER",
                "CUSTOMER",
                id.toString(),
                "Updated profile details for customer " + existing.getAccountNumber(),
                "SYSTEM",
                "SUCCESS"
        );

        return customerRepository.save(existing);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            throw new AccessDeniedException("Customers cannot delete bank accounts directly");
        }

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        log.info("Soft-deleting customer ID: {} with account: {}", id, customer.getAccountNumber());
        // In banking: Mark as DELETED to preserve audit trail, never delete financial transactions
        customer.setStatus(CustomerStatus.DELETED);
        customerRepository.save(customer);

        accountRepository.findByAccountNumber(customer.getAccountNumber()).ifPresent(acc -> {
            acc.setStatus(AccountStatus.CLOSED);
            accountRepository.save(acc);
        });

        auditService.logEvent(
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getRole().name(),
                "DELETE_CUSTOMER",
                "CUSTOMER",
                id.toString(),
                "Marked customer as DELETED. Preserved audit and transaction records.",
                "SYSTEM",
                "SUCCESS"
        );
    }

    private void validateDepositParameters(String accountNumber, BigDecimal amount) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new BankingException("Account number is required.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Deposit amount must be greater than zero.");
        }
    }

    private void validateWithdrawalParameters(String accountNumber, BigDecimal amount) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new BankingException("Account number is required.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Withdrawal amount must be greater than zero.");
        }
    }

    private void validateTransferParameters(String senderAccountNumber, String receiverAccountNumber, BigDecimal amount) {
        if (senderAccountNumber == null || senderAccountNumber.trim().isEmpty()) {
            throw new BankingException("Source account number is required.");
        }
        if (receiverAccountNumber == null || receiverAccountNumber.trim().isEmpty()) {
            throw new BankingException("Destination account number is required.");
        }
        if (senderAccountNumber.trim().equalsIgnoreCase(receiverAccountNumber.trim())) {
            throw new BankingException("Cannot transfer to the same account.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Transfer amount must be greater than zero.");
        }
    }

    private Account syncAccountFromCustomer(Customer customer) {
        return accountRepository.findByAccountNumber(customer.getAccountNumber())
                .orElseGet(() -> {
                    Account acc = Account.builder()
                            .accountNumber(customer.getAccountNumber())
                            .customer(customer)
                            .branch(customer.getBranch())
                            .accountType(AccountType.SAVINGS)
                            .status(customer.getStatus() == CustomerStatus.PENDING_VERIFICATION ? AccountStatus.DORMANT : AccountStatus.ACTIVE)
                            .balance(customer.getBalance() != null ? customer.getBalance() : BigDecimal.ZERO)
                            .ledgerBalance(customer.getBalance() != null ? customer.getBalance() : BigDecimal.ZERO)
                            .currency("INR")
                            .createdAt(LocalDateTime.now())
                            .build();
                    return accountRepository.save(acc);
                });
    }

    /**
     * Deposit with pessimistic locking and double-entry ledger bookkeeping
     */
    @Transactional
    public Customer deposit(String accountNumber, BigDecimal amount, String description) {
        validateDepositParameters(accountNumber, amount);

        // Strict Server-Side Authorization Enforcement:
        // Customers can ONLY deposit into their own accounts.
        // Tellers/Staff can ONLY deposit into accounts within their assigned branch.
        authorizationService.verifyAccountAccess(accountNumber);

        User currentUser = authorizationService.getCurrentUser().orElse(null);

        // Lock Account and Customer
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber).orElse(null);
        Customer customer = null;
        if (account != null) {
            customer = account.getCustomer();
            if (customer != null) {
                customer = customerRepository.findByIdWithLock(customer.getId()).orElse(customer);
            }
        } else {
            customer = customerRepository.findByAccountNumberWithLock(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            account = syncAccountFromCustomer(customer);
        }

        if (customer != null && (customer.getStatus() == CustomerStatus.SUSPENDED || customer.getStatus() == CustomerStatus.BLOCKED || customer.getStatus() == CustomerStatus.DELETED)) {
            throw new BankingException("Account is inactive, blocked, or deleted. Cannot perform deposit.");
        }

        if (account != null && (account.getStatus() == AccountStatus.FROZEN || account.getStatus() == AccountStatus.CLOSED)) {
            throw new BankingException("Account is " + account.getStatus().name().toLowerCase() + ". Deposits are not permitted.");
        }

        // Validate transaction limits
        transactionLimitService.validateTransactionLimits(currentUser, account, amount);

        // Indian Banking Compliance (Rule 114B PAN, Section 269ST, Rule 114E SFT)
        if (customer != null) {
            complianceService.validateCashDeposit(customer, amount);
        }

        if (account != null) {
            account.setBalance(account.getBalance().add(amount));
            account.setLedgerBalance(account.getBalance());
            account = accountRepository.save(account);
        }

        Customer updated = customer;
        if (customer != null && account != null && customer.getAccountNumber().equalsIgnoreCase(account.getAccountNumber())) {
            customer.setBalance(account.getBalance());
            updated = customerRepository.save(customer);
        } else if (customer != null && account == null) {
            customer.setBalance(customer.getBalance().add(amount));
            updated = customerRepository.save(customer);
        }

        Transaction transaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(amount)
                .receiverAccount(updated)
                .targetBankAccount(account)
                .balanceAfterTransaction(account != null ? account.getBalance() : (updated != null ? updated.getBalance() : amount))
                .description(description != null && !description.isBlank() ? description : "Deposit")
                .status(Transaction.TransactionStatus.SUCCESS)
                .initiatedBy(currentUser)
                .channel(currentUser != null && currentUser.getRole() == Role.TELLER ? TransactionChannel.BRANCH : TransactionChannel.ONLINE)
                .build();

        Transaction savedTxn = transactionRepository.save(transaction);

        // Double-entry ledger recording
        ledgerService.recordDepositLedger(savedTxn, updated, account, amount, account != null ? account.getBalance() : updated.getBalance());

        auditService.logEvent(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                currentUser != null ? currentUser.getRole().name() : "SYSTEM",
                "DEPOSIT",
                "ACCOUNT",
                accountNumber,
                "Deposited " + currencyUtil.format(amount) + " into " + accountNumber + ". New balance: " + currencyUtil.format(account != null ? account.getBalance() : updated.getBalance()),
                "SYSTEM",
                "SUCCESS"
        );

        log.info("Deposited {} to account {}", amount, accountNumber);
        return updated != null ? updated : (account != null ? account.getCustomer() : null);
    }

    /**
     * Withdrawal with pessimistic locking, limit validation, dual-control, and double-entry ledger
     */
    @Transactional
    public Customer withdraw(String accountNumber, BigDecimal amount, String description) {
        validateWithdrawalParameters(accountNumber, amount);

        // Enforce ownership: customer can only withdraw from their own account
        authorizationService.verifyAccountAccess(accountNumber);

        User currentUser = authorizationService.getCurrentUser().orElse(null);

        // Lock Account and Customer
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber).orElse(null);
        Customer customer = null;
        if (account != null) {
            customer = account.getCustomer();
            if (customer != null) {
                customer = customerRepository.findByIdWithLock(customer.getId()).orElse(customer);
            }
        } else {
            customer = customerRepository.findByAccountNumberWithLock(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            account = syncAccountFromCustomer(customer);
        }

        if (customer != null && (customer.getStatus() == CustomerStatus.SUSPENDED || customer.getStatus() == CustomerStatus.BLOCKED || customer.getStatus() == CustomerStatus.DELETED)) {
            throw new BankingException("Account is inactive, blocked, or deleted. Cannot perform withdrawal.");
        }

        if (account != null && (account.getStatus() == AccountStatus.FROZEN || account.getStatus() == AccountStatus.CLOSED)) {
            throw new BankingException("Account is " + account.getStatus().name().toLowerCase() + ". Withdrawals are not permitted.");
        }

        // Limit verification
        transactionLimitService.validateTransactionLimits(currentUser, account, amount);

        BigDecimal currentBalance = account != null ? account.getBalance() : (customer != null ? customer.getBalance() : BigDecimal.ZERO);
        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(currentBalance, amount);
        }

        // Check dual-control Maker-Checker threshold
        if (transactionLimitService.requiresApproval(currentUser, amount)) {
            log.info("Withdrawal of {} requires manager approval", currencyUtil.format(amount));
            Transaction pendingTxn = Transaction.builder()
                    .transactionType(Transaction.TransactionType.WITHDRAWAL)
                    .amount(amount)
                    .senderAccount(customer)
                    .sourceBankAccount(account)
                    .description(description != null && !description.isBlank() ? description : "Withdrawal (Pending Approval)")
                    .status(Transaction.TransactionStatus.PENDING_APPROVAL)
                    .initiatedBy(currentUser)
                    .build();
            approvalService.submitForApproval(
                    pendingTxn,
                    currentUser,
                    ApprovalType.HIGH_VALUE_TRANSACTION,
                    amount,
                    accountNumber,
                    null,
                    "High-value withdrawal of " + currencyUtil.format(amount) + " exceeds approval threshold"
            );

            throw new BankingException("Withdrawal of " + currencyUtil.format(amount) +
                    " exceeds approval threshold (" + currencyUtil.format(transactionLimitService.getApprovalThreshold(currentUser != null ? currentUser.getRole() : Role.CUSTOMER)) +
                    "). Submitted for Manager Approval.");
        }

        TdsCalculationResult tdsResult = (customer != null)
                ? complianceService.calculateSection194nCashWithdrawalTds(customer, amount)
                : TdsCalculationResult.builder()
                        .grossAmount(amount)
                        .tdsAmount(BigDecimal.ZERO)
                        .netPayout(amount)
                        .explanation("No TDS")
                        .build();

        if (account != null) {
            account.setBalance(account.getBalance().subtract(amount));
            account.setLedgerBalance(account.getBalance());
            account = accountRepository.save(account);
        }

        Customer updated = customer;
        if (customer != null && account != null && customer.getAccountNumber().equalsIgnoreCase(account.getAccountNumber())) {
            customer.setBalance(account.getBalance());
            updated = customerRepository.save(customer);
        } else if (customer != null && account == null) {
            customer.setBalance(customer.getBalance().subtract(amount));
            updated = customerRepository.save(customer);
        }

        String desc = description != null && !description.isBlank() ? description : "Withdrawal";
        if (tdsResult.getTdsAmount().compareTo(BigDecimal.ZERO) > 0) {
            desc += " [Sec 194N TDS: " + currencyUtil.format(tdsResult.getTdsAmount()) + ", Net: " + currencyUtil.format(tdsResult.getNetPayout()) + "]";
        }

        Transaction transaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(amount)
                .fee(tdsResult.getTdsAmount())
                .senderAccount(updated)
                .sourceBankAccount(account)
                .balanceAfterTransaction(account != null ? account.getBalance() : (updated != null ? updated.getBalance() : BigDecimal.ZERO))
                .description(desc)
                .status(Transaction.TransactionStatus.SUCCESS)
                .initiatedBy(currentUser)
                .channel(currentUser != null && currentUser.getRole() == Role.TELLER ? TransactionChannel.BRANCH : TransactionChannel.ONLINE)
                .build();

        Transaction savedTxn = transactionRepository.save(transaction);

        if (tdsResult.getTdsAmount().compareTo(BigDecimal.ZERO) > 0) {
            auditService.logEvent(
                    currentUser != null ? currentUser.getId() : null,
                    currentUser != null ? currentUser.getUsername() : "SYSTEM",
                    currentUser != null ? currentUser.getRole().name() : "SYSTEM",
                    "TDS_194N_DEDUCTED",
                    "TAX_COMPLIANCE",
                    accountNumber,
                    tdsResult.getExplanation(),
                    "127.0.0.1",
                    "SUCCESS"
            );
        }

        // Double-entry ledger recording
        ledgerService.recordWithdrawalLedger(savedTxn, updated, account, amount, account != null ? account.getBalance() : updated.getBalance());

        auditService.logEvent(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                currentUser != null ? currentUser.getRole().name() : "SYSTEM",
                "WITHDRAWAL",
                "ACCOUNT",
                accountNumber,
                "Withdrew " + currencyUtil.format(amount) + " from " + accountNumber + ". Remaining balance: " + currencyUtil.format(account != null ? account.getBalance() : updated.getBalance()),
                "SYSTEM",
                "SUCCESS"
        );

        log.info("Withdrew {} from account {}", amount, accountNumber);
        return updated != null ? updated : (account != null ? account.getCustomer() : null);
    }

    /**
     * Transfer with default internal channel (Intra-bank transfer)
     */
    @Transactional
    public void transfer(String senderAccountNumber, String receiverAccountNumber, BigDecimal amount, String description) {
        transfer(senderAccountNumber, receiverAccountNumber, amount, description, CompliancePaymentChannel.INTERNAL);
    }

    /**
     * Transfer with payment channel compliance verification (RTGS, NEFT, IMPS, UPI, INTERNAL)
     */
    @Transactional
    public void transfer(String senderAccountNumber, String receiverAccountNumber, BigDecimal amount, String description, CompliancePaymentChannel channel) {
        validateTransferParameters(senderAccountNumber, receiverAccountNumber, amount);

        if (channel == null) {
            channel = CompliancePaymentChannel.INTERNAL;
        }

        // Strict Ownership verification: logged in user must own the sender account!
        authorizationService.verifyAccountAccess(senderAccountNumber);

        User currentUser = authorizationService.getCurrentUser().orElse(null);

        // Fast-fail check for rapid duplicate submissions before locking
        LocalDateTime debounceCutoff = LocalDateTime.now().minusSeconds(3);
        if (transactionRepository.existsRecentDuplicateTransfer(senderAccountNumber, receiverAccountNumber, amount, debounceCutoff)) {
            throw new BankingException("A duplicate transfer of the same amount between these accounts was just processed. Please wait a few moments before trying again.");
        }

        // Acquire pessimistic write locks in consistent alphanumeric order to prevent deadlocks
        String firstLockNum = senderAccountNumber.compareToIgnoreCase(receiverAccountNumber) < 0 ? senderAccountNumber : receiverAccountNumber;
        String secondLockNum = senderAccountNumber.compareToIgnoreCase(receiverAccountNumber) < 0 ? receiverAccountNumber : senderAccountNumber;

        Account firstAcc = accountRepository.findByAccountNumberWithLock(firstLockNum).orElse(null);
        Customer firstCust = null;
        if (firstAcc == null) {
            firstCust = customerRepository.findByAccountNumberWithLock(firstLockNum).orElse(null);
            if (firstCust == null) {
                throw new AccountNotFoundException("Account not found: " + firstLockNum);
            }
            firstAcc = syncAccountFromCustomer(firstCust);
        } else {
            firstCust = firstAcc.getCustomer();
        }

        Account secondAcc = accountRepository.findByAccountNumberWithLock(secondLockNum).orElse(null);
        Customer secondCust = null;
        if (secondAcc == null) {
            secondCust = customerRepository.findByAccountNumberWithLock(secondLockNum).orElse(null);
            if (secondCust == null) {
                throw new AccountNotFoundException("Account not found: " + secondLockNum);
            }
            secondAcc = syncAccountFromCustomer(secondCust);
        } else {
            secondCust = secondAcc.getCustomer();
        }

        Account senderAccount = senderAccountNumber.equalsIgnoreCase(firstLockNum) ? firstAcc : secondAcc;
        Customer sender = senderAccountNumber.equalsIgnoreCase(firstLockNum) ? firstCust : secondCust;

        Account receiverAccount = senderAccountNumber.equalsIgnoreCase(firstLockNum) ? secondAcc : firstAcc;
        Customer receiver = senderAccountNumber.equalsIgnoreCase(firstLockNum) ? secondCust : firstCust;

        // Consistent customer locking by ID order
        if (sender != null && receiver != null) {
            if (sender.getId().equals(receiver.getId())) {
                Customer lockedCust = customerRepository.findByIdWithLock(sender.getId()).orElse(sender);
                sender = lockedCust;
                receiver = lockedCust;
            } else {
                Long minId = Math.min(sender.getId(), receiver.getId());
                Long maxId = Math.max(sender.getId(), receiver.getId());
                Customer cMin = customerRepository.findByIdWithLock(minId).orElse(null);
                Customer cMax = customerRepository.findByIdWithLock(maxId).orElse(null);
                if (sender.getId().equals(minId)) {
                    sender = (cMin != null) ? cMin : sender;
                    receiver = (cMax != null) ? cMax : receiver;
                } else {
                    sender = (cMax != null) ? cMax : sender;
                    receiver = (cMin != null) ? cMin : receiver;
                }
            }
        }

        // Authoritative post-lock duplicate check: serializes concurrent duplicate submissions
        if (transactionRepository.existsRecentDuplicateTransfer(senderAccountNumber, receiverAccountNumber, amount, LocalDateTime.now().minusSeconds(3))) {
            throw new BankingException("A duplicate transfer of the same amount between these accounts was just processed. Please wait a few moments before trying again.");
        }

        // Validate account statuses: FROZEN and CLOSED accounts are strictly blocked.
        // In this demo application, DORMANT status alone does not prevent an otherwise eligible internal transfer.
        if (sender != null && (sender.getStatus() == CustomerStatus.SUSPENDED || sender.getStatus() == CustomerStatus.BLOCKED || sender.getStatus() == CustomerStatus.DELETED)) {
            throw new BankingException("Sender account is inactive, blocked, or deleted.");
        }
        if (senderAccount != null && (senderAccount.getStatus() == AccountStatus.FROZEN || senderAccount.getStatus() == AccountStatus.CLOSED)) {
            throw new BankingException("Sender account is " + senderAccount.getStatus().name().toLowerCase() + ". Transfers are not permitted.");
        }

        if (receiver != null && (receiver.getStatus() == CustomerStatus.SUSPENDED || receiver.getStatus() == CustomerStatus.BLOCKED || receiver.getStatus() == CustomerStatus.DELETED)) {
            throw new BankingException("Receiver account is inactive, blocked, or deleted.");
        }
        if (receiverAccount != null && (receiverAccount.getStatus() == AccountStatus.FROZEN || receiverAccount.getStatus() == AccountStatus.CLOSED)) {
            throw new BankingException("Receiver account is " + receiverAccount.getStatus().name().toLowerCase() + ". Transfers are not permitted.");
        }

        // Channel-specific statutory and operational compliance check (RTGS min ₹2L, UPI max ₹1L, IMPS max ₹5L, NEFT max ₹10L)
        if (sender != null && receiver != null) {
            complianceService.validateTransfer(sender, receiver, amount, channel);
        }

        // Limit verification: internal transfer checks single-transaction and daily limits
        if (channel == CompliancePaymentChannel.INTERNAL) {
            transactionLimitService.validateTransactionLimits(currentUser, senderAccount, amount);
        }

        // Fraud velocity / risk check
        if (sender != null) {
            fraudRiskService.assessTransactionRisk(sender, amount);
        }

        BigDecimal senderBal = senderAccount != null ? senderAccount.getBalance() : (sender != null ? sender.getBalance() : BigDecimal.ZERO);
        if (senderBal.compareTo(amount) < 0) {
            throw new InsufficientFundsException(senderBal, amount);
        }

        // Maker-Checker Dual-control check
        if (transactionLimitService.requiresApproval(currentUser, amount)) {
            log.info("Transfer of {} requires manager approval", currencyUtil.format(amount));
            Transaction pendingTxn = Transaction.builder()
                    .transactionType(Transaction.TransactionType.TRANSFER)
                    .amount(amount)
                    .senderAccount(sender)
                    .receiverAccount(receiver)
                    .sourceBankAccount(senderAccount)
                    .targetBankAccount(receiverAccount)
                    .description(description != null && !description.isBlank() ? description : "Fund Transfer (Pending Approval)")
                    .status(Transaction.TransactionStatus.PENDING_APPROVAL)
                    .initiatedBy(currentUser)
                    .build();
            approvalService.submitForApproval(
                    pendingTxn,
                    currentUser,
                    ApprovalType.HIGH_VALUE_TRANSACTION,
                    amount,
                    senderAccountNumber,
                    receiverAccountNumber,
                    "High-value transfer of " + currencyUtil.format(amount) + " exceeds threshold"
            );

            throw new BankingException("Transfer of " + currencyUtil.format(amount) +
                    " exceeds approval threshold (" + currencyUtil.format(transactionLimitService.getApprovalThreshold(currentUser != null ? currentUser.getRole() : Role.CUSTOMER)) +
                    "). Submitted for Dual-Control Manager Approval.");
        }

        // Update sender balance and daily transfer total
        if (senderAccount != null) {
            senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
            senderAccount.setLedgerBalance(senderAccount.getBalance());

            LocalDate today = LocalDate.now();
            BigDecimal currentDaily = (today.equals(senderAccount.getDailyTransferDate()) && senderAccount.getDailyTransferTotal() != null)
                    ? senderAccount.getDailyTransferTotal()
                    : BigDecimal.ZERO;
            senderAccount.setDailyTransferDate(today);
            senderAccount.setDailyTransferTotal(currentDaily.add(amount));
            senderAccount = accountRepository.save(senderAccount);
        }

        // Update receiver balance
        if (receiverAccount != null) {
            receiverAccount.setBalance(receiverAccount.getBalance().add(amount));
            receiverAccount.setLedgerBalance(receiverAccount.getBalance());
            receiverAccount = accountRepository.save(receiverAccount);
        }

        // Update customer primary balances if affected
        if (sender != null && senderAccount != null && sender.getAccountNumber().equalsIgnoreCase(senderAccount.getAccountNumber())) {
            sender.setBalance(senderAccount.getBalance());
            sender = customerRepository.save(sender);
        } else if (sender != null && senderAccount == null) {
            sender.setBalance(sender.getBalance().subtract(amount));
            sender = customerRepository.save(sender);
        }

        if (receiver != null && receiverAccount != null && receiver.getAccountNumber().equalsIgnoreCase(receiverAccount.getAccountNumber())) {
            receiver.setBalance(receiverAccount.getBalance());
            receiver = customerRepository.save(receiver);
        } else if (receiver != null && receiverAccount == null) {
            receiver.setBalance(receiver.getBalance().add(amount));
            receiver = customerRepository.save(receiver);
        }

        String desc = description != null && !description.isBlank() ? description : "Fund Transfer";
        String channelTag = (channel != null && channel != CompliancePaymentChannel.INTERNAL) ? " [" + channel.name() + "]" : "";

        String senderTargetLabel = (receiver != null && !receiver.getId().equals(sender != null ? sender.getId() : -1L))
                ? receiver.getName()
                : receiverAccountNumber;
        String receiverSourceLabel = (sender != null && !sender.getId().equals(receiver != null ? receiver.getId() : -1L))
                ? sender.getName()
                : senderAccountNumber;

        BigDecimal senderBalAfter = senderAccount != null ? senderAccount.getBalance() : (sender != null ? sender.getBalance() : BigDecimal.ZERO);
        BigDecimal receiverBalAfter = receiverAccount != null ? receiverAccount.getBalance() : (receiver != null ? receiver.getBalance() : BigDecimal.ZERO);

        Transaction sentTransaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_SENT)
                .amount(amount)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .sourceBankAccount(senderAccount)
                .targetBankAccount(receiverAccount)
                .balanceAfterTransaction(senderBalAfter)
                .description(desc + channelTag + " to " + senderTargetLabel)
                .status(Transaction.TransactionStatus.SUCCESS)
                .initiatedBy(currentUser)
                .channel(currentUser != null && currentUser.getRole() == Role.TELLER ? TransactionChannel.BRANCH : TransactionChannel.ONLINE)
                .build();

        Transaction receivedTransaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_RECEIVED)
                .amount(amount)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .sourceBankAccount(senderAccount)
                .targetBankAccount(receiverAccount)
                .balanceAfterTransaction(receiverBalAfter)
                .description(desc + channelTag + " from " + receiverSourceLabel)
                .status(Transaction.TransactionStatus.SUCCESS)
                .initiatedBy(currentUser)
                .channel(currentUser != null && currentUser.getRole() == Role.TELLER ? TransactionChannel.BRANCH : TransactionChannel.ONLINE)
                .build();

        Transaction savedSent = transactionRepository.save(sentTransaction);
        transactionRepository.save(receivedTransaction);

        // Double-entry ledger recording
        ledgerService.recordTransferLedger(savedSent, sender, receiver, senderAccount, receiverAccount, amount, senderBalAfter, receiverBalAfter);

        auditService.logEvent(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                currentUser != null ? currentUser.getRole().name() : "SYSTEM",
                "TRANSFER",
                "ACCOUNT",
                senderAccountNumber,
                "Transferred " + currencyUtil.format(amount) + " to " + receiverAccountNumber + ". Sender remaining balance: " + currencyUtil.format(senderBalAfter),
                "SYSTEM",
                "SUCCESS"
        );

        log.info("Transferred {} from account {} to account {}", amount, senderAccountNumber, receiverAccountNumber);
    }

    private String generateAccountNumber() {
        Random random = new Random();
        String accountNumber;
        do {
            long number = (long) (random.nextDouble() * 9_000_000_000L) + 1_000_000_000L;
            accountNumber = "SB" + number;
        } while (customerRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    @Transactional(readOnly = true)
    public long countTotalCustomers() {
        return customerRepository.countTotalCustomers();
    }

    @Transactional(readOnly = true)
    public long countCustomersForBranch(Long branchId) {
        if (branchId == null) {
            return countTotalCustomers();
        }
        return customerRepository.countActiveCustomersByBranchId(branchId);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumTotalBalance() {
        if (accountRepository.count() > 0) {
            BigDecimal accountTotal = accountRepository.sumTotalBalance();
            if (accountTotal != null) {
                return accountTotal;
            }
        }
        BigDecimal total = customerRepository.sumTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal sumBalanceForBranch(Long branchId) {
        if (branchId == null) {
            return sumTotalBalance();
        }
        return accountRepository.sumBalanceByBranchId(branchId);
    }

    @Transactional(readOnly = true)
    public Page<BranchCustomerDto> getBranchCustomers(User manager, String query, Pageable pageable) {
        if (manager == null) {
            throw new AccessDeniedException("Authentication required.");
        }

        Page<Customer> page;
        boolean hasQuery = query != null && !query.trim().isEmpty();

        if (manager.getRole() == Role.BRANCH_MANAGER) {
            if (manager.getBranch() == null) {
                log.warn("[BRANCH SCOPE ERROR] Branch Manager {} has no assigned branch", manager.getUsername());
                throw new AccessDeniedException("Forbidden: No branch assigned to manager.");
            }
            Long branchId = manager.getBranch().getId();
            if (hasQuery) {
                page = customerRepository.searchActiveCustomersByBranchId(branchId, query.trim(), pageable);
            } else {
                page = customerRepository.findActiveCustomersByBranchId(branchId, pageable);
            }
        } else {
            // Regional Manager or Super Admin without single branch restriction
            if (manager.getBranch() != null) {
                Long branchId = manager.getBranch().getId();
                if (hasQuery) {
                    page = customerRepository.searchActiveCustomersByBranchId(branchId, query.trim(), pageable);
                } else {
                    page = customerRepository.findActiveCustomersByBranchId(branchId, pageable);
                }
            } else {
                if (hasQuery) {
                    page = customerRepository.searchAllActiveCustomers(query.trim(), pageable);
                } else {
                    page = customerRepository.findAllActiveCustomers(pageable);
                }
            }
        }

        return page.map(this::mapToBranchCustomerDto);
    }

    @Transactional(readOnly = true)
    public Customer getBranchCustomerById(Long id, User manager) {
        Customer customer = customerRepository.findByIdWithBranch(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        authorizationService.verifyManagerBranchAccess(manager, customer);
        return customer;
    }

    private BranchCustomerDto mapToBranchCustomerDto(Customer customer) {
        AccountType accType = accountRepository.findByAccountNumber(customer.getAccountNumber())
                .map(Account::getAccountType)
                .orElse(AccountType.SAVINGS);

        Branch branch = customer.getBranch();
        String bName = branch != null ? branch.getBranchName() : "Unassigned";
        String bCode = branch != null ? branch.getBranchCode() : "N/A";

        return BranchCustomerDto.builder()
                .id(customer.getId())
                .name(customer.getName())
                .rawAccountNumber(customer.getAccountNumber())
                .maskedAccountNumber(BranchCustomerDto.maskAccountNumber(customer.getAccountNumber()))
                .customerNumber(customer.getCustomerNumber())
                .accountType(accType)
                .status(customer.getStatus())
                .kycStatus(customer.getKycStatus())
                .branchName(bName)
                .branchCode(bCode)
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
