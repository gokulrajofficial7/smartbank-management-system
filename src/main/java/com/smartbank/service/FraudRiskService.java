package com.smartbank.service;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import com.smartbank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudRiskService {

    private final TransactionRepository transactionRepository;
    private static final BigDecimal HIGH_AMOUNT_FLAG = new BigDecimal("20000.00");
    private static final int VELOCITY_WINDOW_MINUTES = 10;
    private static final int MAX_VELOCITY_COUNT = 5;

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public RiskLevel assessTransactionRisk(Customer sender, BigDecimal amount) {
        if (sender == null) return RiskLevel.LOW;

        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(VELOCITY_WINDOW_MINUTES);
        List<Transaction> recentTxns = transactionRepository.findByDateRange(tenMinutesAgo, LocalDateTime.now());

        long countForCustomer = recentTxns.stream()
                .filter(t -> (t.getSenderAccount() != null && t.getSenderAccount().getId().equals(sender.getId())))
                .count();

        if (countForCustomer >= MAX_VELOCITY_COUNT) {
            log.warn("[FRAUD ALERT] High transaction velocity detected for customer {}: {} transactions in 10 mins",
                    sender.getAccountNumber(), countForCustomer);
            return RiskLevel.HIGH;
        }

        if (amount.compareTo(HIGH_AMOUNT_FLAG) >= 0) {
            log.info("[RISK MONITOR] Elevated transaction amount: ₹{} from account {}",
                    amount, sender.getAccountNumber());
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }
}
