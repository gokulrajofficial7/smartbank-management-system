package com.smartbank.config;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            return;
        }

        log.info("Database is empty. Initializing sample SmartBank accounts...");

        Customer appu = Customer.builder()
                .name("Appu")
                .email("appu@smartbank.com")
                .phone("9876543210")
                .accountNumber("SB3532006893")
                .balance(new BigDecimal("156000.00"))
                .build();

        Customer gokul = Customer.builder()
                .name("Gokul Raj")
                .email("gokul@smartbank.com")
                .phone("9876543211")
                .accountNumber("SB7505485085")
                .balance(new BigDecimal("15000.00"))
                .build();

        Customer tharun = Customer.builder()
                .name("Tharun")
                .email("tharun@smartbank.com")
                .phone("9876543212")
                .accountNumber("SB2929788625")
                .balance(new BigDecimal("45000.00"))
                .build();

        Customer shankar = Customer.builder()
                .name("shankar")
                .email("shankar@smartbank.com")
                .phone("9876543213")
                .accountNumber("SB5748533077")
                .balance(new BigDecimal("100000.00"))
                .build();

        customerRepository.save(appu);
        customerRepository.save(gokul);
        customerRepository.save(tharun);
        customerRepository.save(shankar);

        Transaction t1 = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("1000.00"))
                .receiverAccount(appu)
                .balanceAfterTransaction(appu.getBalance())
                .description("Cash Deposit")
                .transactionDate(LocalDateTime.now().minusDays(2))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t2 = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("200000.00"))
                .receiverAccount(appu)
                .balanceAfterTransaction(appu.getBalance())
                .description("Cash Deposit")
                .transactionDate(LocalDateTime.now().minusDays(1))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t3 = Transaction.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("50000.00"))
                .senderAccount(appu)
                .balanceAfterTransaction(appu.getBalance())
                .description("withdrawal")
                .transactionDate(LocalDateTime.now().minusHours(5))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t4 = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_SENT)
                .amount(new BigDecimal("5000.00"))
                .senderAccount(tharun)
                .receiverAccount(gokul)
                .balanceAfterTransaction(tharun.getBalance())
                .description("Rent payment to Gokul Raj")
                .transactionDate(LocalDateTime.now().minusHours(2))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t5 = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_RECEIVED)
                .amount(new BigDecimal("5000.00"))
                .senderAccount(tharun)
                .receiverAccount(gokul)
                .balanceAfterTransaction(gokul.getBalance())
                .description("Rent payment from Tharun")
                .transactionDate(LocalDateTime.now().minusHours(2))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        transactionRepository.save(t1);
        transactionRepository.save(t2);
        transactionRepository.save(t3);
        transactionRepository.save(t4);
        transactionRepository.save(t5);

        log.info("SmartBank sample accounts and transactions initialized successfully.");
    }
}
