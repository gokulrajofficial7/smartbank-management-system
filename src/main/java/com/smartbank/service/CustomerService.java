package com.smartbank.service;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.CustomerNotFoundException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Customer createCustomer(Customer customer) {
        log.info("Creating new customer with email: {}", customer.getEmail());

        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new BankingException("A customer with email '" + customer.getEmail() + "' already exists.");
        }

        String accountNumber = generateAccountNumber();
        customer.setAccountNumber(accountNumber);

        if (customer.getBalance() == null) {
            customer.setBalance(BigDecimal.ZERO);
        }

        Customer saved = customerRepository.save(customer);
        log.info("Customer created successfully with account number: {}", accountNumber);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Customer getCustomerByAccountNumber(String accountNumber) {
        return customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new CustomerNotFoundException("account number", accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String query) {
        return customerRepository.searchCustomers(query);
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer updatedCustomer) {
        Customer existing = getCustomerById(id);

        if (!existing.getEmail().equals(updatedCustomer.getEmail())
                && customerRepository.existsByEmail(updatedCustomer.getEmail())) {
            throw new BankingException("Email '" + updatedCustomer.getEmail() + "' is already in use.");
        }

        existing.setName(updatedCustomer.getName());
        existing.setEmail(updatedCustomer.getEmail());
        existing.setPhone(updatedCustomer.getPhone());

        return customerRepository.save(existing);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        log.info("Deleting customer with ID: {} and account number: {}", id, customer.getAccountNumber());
        customerRepository.delete(customer);
    }

    @Transactional
    public Customer deposit(String accountNumber, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Deposit amount must be greater than zero.");
        }

        Customer customer = getCustomerByAccountNumber(accountNumber);
        customer.setBalance(customer.getBalance().add(amount));
        Customer updated = customerRepository.save(customer);

        Transaction transaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(amount)
                .receiverAccount(updated)
                .balanceAfterTransaction(updated.getBalance())
                .description(description != null ? description : "Deposit")
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        transactionRepository.save(transaction);
        log.info("Deposited {} to account {}", amount, accountNumber);
        return updated;
    }

    @Transactional
    public Customer withdraw(String accountNumber, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Withdrawal amount must be greater than zero.");
        }

        Customer customer = getCustomerByAccountNumber(accountNumber);

        if (customer.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(customer.getBalance(), amount);
        }

        customer.setBalance(customer.getBalance().subtract(amount));
        Customer updated = customerRepository.save(customer);

        Transaction transaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(amount)
                .senderAccount(updated)
                .balanceAfterTransaction(updated.getBalance())
                .description(description != null ? description : "Withdrawal")
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        transactionRepository.save(transaction);
        log.info("Withdrew {} from account {}", amount, accountNumber);
        return updated;
    }

    @Transactional
    public void transfer(String senderAccountNumber, String receiverAccountNumber, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankingException("Transfer amount must be greater than zero.");
        }

        if (senderAccountNumber.equals(receiverAccountNumber)) {
            throw new BankingException("Cannot transfer to the same account.");
        }

        Customer sender = getCustomerByAccountNumber(senderAccountNumber);
        Customer receiver = getCustomerByAccountNumber(receiverAccountNumber);

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(sender.getBalance(), amount);
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        customerRepository.save(sender);
        customerRepository.save(receiver);

        String desc = description != null ? description : "Transfer";

        Transaction sentTransaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_SENT)
                .amount(amount)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .balanceAfterTransaction(sender.getBalance())
                .description(desc + " to " + receiver.getName())
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction receivedTransaction = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_RECEIVED)
                .amount(amount)
                .senderAccount(sender)
                .receiverAccount(receiver)
                .balanceAfterTransaction(receiver.getBalance())
                .description(desc + " from " + sender.getName())
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        transactionRepository.save(sentTransaction);
        transactionRepository.save(receivedTransaction);

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
    public BigDecimal sumTotalBalance() {
        BigDecimal total = customerRepository.sumTotalBalance();
        return total != null ? total : BigDecimal.ZERO;
    }
}
