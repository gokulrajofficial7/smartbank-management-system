package com.smartbank.service;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.*;
import com.smartbank.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BankCardRepository bankCardRepository;
    private final FixedDepositRepository fixedDepositRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Account> getMyAccounts() {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found for current user"));
        return accountRepository.findByCustomerId(customer.getId());
    }

    @Transactional(readOnly = true)
    public Account getAccountByNumber(String accountNumber) {
        authorizationService.verifyAccountAccess(accountNumber);
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankingException("Account not found: " + accountNumber));
    }

    @Transactional
    public void freezeAccount(String accountNumber, String reason) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new BankingException("Account not found: " + accountNumber));

        account.setStatus(AccountStatus.FROZEN);
        accountRepository.save(account);

        customerRepository.findByAccountNumber(accountNumber).ifPresent(c -> {
            c.setStatus(CustomerStatus.BLOCKED);
            customerRepository.save(c);
        });

        auditService.logEvent(
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getRole().name(),
                "ACCOUNT_FREEZE",
                "ACCOUNT",
                accountNumber,
                "Account frozen by " + currentUser.getUsername() + ". Reason: " + reason,
                "SYSTEM",
                "SUCCESS"
        );
        log.warn("[COMPLIANCE] Account {} FROZEN. Reason: {}", accountNumber, reason);
    }

    @Transactional
    public void unfreezeAccount(String accountNumber, String reason) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new BankingException("Account not found: " + accountNumber));

        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        customerRepository.findByAccountNumber(accountNumber).ifPresent(c -> {
            c.setStatus(CustomerStatus.ACTIVE);
            customerRepository.save(c);
        });

        auditService.logEvent(
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getRole().name(),
                "ACCOUNT_UNFREEZE",
                "ACCOUNT",
                accountNumber,
                "Account un-frozen by " + currentUser.getUsername() + ". Reason: " + reason,
                "SYSTEM",
                "SUCCESS"
        );
        log.info("[COMPLIANCE] Account {} UN-FROZEN. Reason: {}", accountNumber, reason);
    }

    // Beneficiary management with cooling-off period
    @Transactional
    public Beneficiary addBeneficiary(String targetAccountNumber, String targetName, String bankName, String nickname) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));

        if (targetAccountNumber == null || targetAccountNumber.trim().isEmpty()) {
            throw new BankingException("Beneficiary account number is required.");
        }
        String cleanTargetAcc = targetAccountNumber.trim();

        if (targetName == null || targetName.trim().isEmpty()) {
            throw new BankingException("Beneficiary account holder name is required.");
        }
        String cleanTargetName = targetName.trim();

        // Prevent adding own accounts (check primary account number and all accounts owned by this customer)
        boolean isOwnAccount = cleanTargetAcc.equalsIgnoreCase(customer.getAccountNumber())
                || accountRepository.findByCustomerId(customer.getId()).stream()
                        .anyMatch(acc -> acc.getAccountNumber().equalsIgnoreCase(cleanTargetAcc));
        if (isOwnAccount) {
            throw new BankingException("Cannot add your own account as a beneficiary.");
        }

        String resolvedBank = (bankName != null && !bankName.trim().isEmpty()) ? bankName.trim() : "SmartBank";
        String resolvedNickname = (nickname != null && !nickname.trim().isEmpty()) ? nickname.trim() : null;

        // If internal SmartBank, validate that the account exists and is not closed
        if ("SmartBank".equalsIgnoreCase(resolvedBank)) {
            Optional<Account> targetAccountOpt = accountRepository.findByAccountNumber(cleanTargetAcc);
            if (targetAccountOpt.isEmpty() && !customerRepository.existsByAccountNumber(cleanTargetAcc)) {
                throw new BankingException("Target SmartBank account " + cleanTargetAcc + " was not found. Please verify the account number.");
            }
            if (targetAccountOpt.isPresent() && targetAccountOpt.get().getStatus() == AccountStatus.CLOSED) {
                throw new BankingException("Target SmartBank account " + cleanTargetAcc + " is closed and cannot receive funds.");
            }
        }

        // Duplicate check for active beneficiary
        Optional<Beneficiary> existing = beneficiaryRepository.findByCustomerIdAndTargetAccountNumber(customer.getId(), cleanTargetAcc);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getActive())) {
            throw new BankingException("Beneficiary account " + cleanTargetAcc + " is already in your active beneficiary list.");
        }

        // Apply a 4-hour security cooling-off period
        LocalDateTime coolingOff = LocalDateTime.now().plusHours(4);

        Beneficiary beneficiary;
        if (existing.isPresent()) {
            beneficiary = existing.get();
            beneficiary.setActive(true);
            beneficiary.setTargetAccountName(cleanTargetName);
            beneficiary.setBankName(resolvedBank);
            beneficiary.setNickname(resolvedNickname);
            beneficiary.setCoolingOffUntil(coolingOff);
        } else {
            beneficiary = Beneficiary.builder()
                    .customer(customer)
                    .targetAccountNumber(cleanTargetAcc)
                    .targetAccountName(cleanTargetName)
                    .bankName(resolvedBank)
                    .nickname(resolvedNickname)
                    .coolingOffUntil(coolingOff)
                    .active(true)
                    .build();
        }

        Beneficiary saved = beneficiaryRepository.save(beneficiary);

        User currentUser = authorizationService.getRequiredCurrentUser();
        auditService.logEvent(
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getRole().name(),
                "ADD_BENEFICIARY",
                "BENEFICIARY",
                saved.getId().toString(),
                "Added beneficiary " + cleanTargetAcc + " (" + cleanTargetName + ") with 4h cooling-off period until " + coolingOff,
                "SYSTEM",
                "SUCCESS"
        );

        return saved;
    }

    @Transactional
    public void deleteBeneficiary(Long beneficiaryId) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));

        if (beneficiaryId == null) {
            throw new BankingException("Beneficiary identifier is required.");
        }

        Beneficiary beneficiary = beneficiaryRepository.findByIdAndCustomerId(beneficiaryId, customer.getId())
                .orElseThrow(() -> new BankingException("Beneficiary record not found or access denied."));

        beneficiary.setActive(false);
        beneficiaryRepository.save(beneficiary);

        User currentUser = authorizationService.getRequiredCurrentUser();
        auditService.logEvent(
                currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getRole().name(),
                "DELETE_BENEFICIARY",
                "BENEFICIARY",
                beneficiary.getId().toString(),
                "Deactivated beneficiary " + beneficiary.getTargetAccountNumber(),
                "SYSTEM",
                "SUCCESS"
        );
    }

    @Transactional(readOnly = true)
    public List<Beneficiary> getMyBeneficiaries() {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        return beneficiaryRepository.findByCustomerIdAndActiveTrue(customer.getId());
    }

    // Fixed Deposit creation
    @Transactional
    public FixedDeposit openFixedDeposit(String accountNumber, BigDecimal amount, int tenureMonths) {
        authorizationService.verifyAccountAccess(accountNumber);
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));

        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new BankingException("Account not found: " + accountNumber));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new BankingException("Insufficient funds in account to open Fixed Deposit.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        BigDecimal rate = BigDecimal.valueOf(6.50); // 6.5% interest
        BigDecimal interest = amount.multiply(rate).multiply(BigDecimal.valueOf(tenureMonths))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal maturityAmount = amount.add(interest);

        FixedDeposit fd = FixedDeposit.builder()
                .account(account)
                .customer(customer)
                .depositNumber("FD-" + System.currentTimeMillis())
                .principalAmount(amount)
                .interestRate(rate)
                .tenureMonths(tenureMonths)
                .maturityAmount(maturityAmount)
                .maturityDate(LocalDate.now().plusMonths(tenureMonths))
                .status("ACTIVE")
                .build();

        return fixedDepositRepository.save(fd);
    }

    // Loan Application
    @Transactional
    public LoanApplication applyLoan(BigDecimal amount, int termMonths, String purpose) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));

        LoanApplication loan = LoanApplication.builder()
                .customer(customer)
                .amount(amount)
                .termMonths(termMonths)
                .purpose(purpose)
                .status("APPLIED")
                .interestRate(new BigDecimal("8.50"))
                .build();

        return loanApplicationRepository.save(loan);
    }
}
