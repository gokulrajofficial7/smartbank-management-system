package com.smartbank.service;

import com.smartbank.entity.Account;
import com.smartbank.entity.Role;
import com.smartbank.entity.TransactionLimit;
import com.smartbank.entity.User;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.TransactionLimitRepository;
import com.smartbank.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionLimitService {

    private final TransactionLimitRepository transactionLimitRepository;

    // Standard default limits if not configured in database
    private static final BigDecimal DEFAULT_SINGLE_LIMIT = new BigDecimal("50000.00");
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("200000.00");
    private static final BigDecimal DEFAULT_APPROVAL_THRESHOLD = new BigDecimal("25000.00");

    public void validateTransactionLimits(User user, Account account, BigDecimal amount) {
        Role role = (user != null) ? user.getRole() : Role.CUSTOMER;
        TransactionLimit limits = getEffectiveLimits(role);

        // 1. Single transaction limit check
        if (amount.compareTo(limits.getSingleTransactionLimit()) > 0) {
            log.warn("Transaction amount {} exceeds single transaction limit of {}",
                    amount, limits.getSingleTransactionLimit());
            throw new BankingException("Transaction amount " + CurrencyUtil.format(amount) +
                    " exceeds single transaction limit of " + CurrencyUtil.format(limits.getSingleTransactionLimit()));
        }

        // 2. Daily limit check (if account is tracked)
        if (account != null) {
            LocalDate today = LocalDate.now();
            BigDecimal dailyTotal = BigDecimal.ZERO;
            if (today.equals(account.getDailyTransferDate()) && account.getDailyTransferTotal() != null) {
                dailyTotal = account.getDailyTransferTotal();
            }

            BigDecimal projectedDaily = dailyTotal.add(amount);
            if (projectedDaily.compareTo(limits.getDailyLimit()) > 0) {
                log.warn("Account {} daily limit exceeded: current {}, requested {}, limit {}",
                        account.getAccountNumber(), dailyTotal, amount, limits.getDailyLimit());
                throw new BankingException("Daily limit of " + CurrencyUtil.format(limits.getDailyLimit()) +
                        " would be exceeded. Today's current total: " + CurrencyUtil.format(dailyTotal));
            }
        }
    }

    public boolean requiresApproval(User user, BigDecimal amount) {
        Role role = (user != null) ? user.getRole() : Role.CUSTOMER;
        TransactionLimit limits = getEffectiveLimits(role);
        return amount.compareTo(limits.getApprovalThreshold()) >= 0;
    }

    public BigDecimal getApprovalThreshold(Role role) {
        return getEffectiveLimits(role).getApprovalThreshold();
    }

    private TransactionLimit getEffectiveLimits(Role role) {
        Optional<TransactionLimit> limitOpt = transactionLimitRepository.findByRole(role);
        return limitOpt.orElseGet(() -> TransactionLimit.builder()
                .role(role)
                .singleTransactionLimit(DEFAULT_SINGLE_LIMIT)
                .dailyLimit(DEFAULT_DAILY_LIMIT)
                .approvalThreshold(DEFAULT_APPROVAL_THRESHOLD)
                .build());
    }
}
