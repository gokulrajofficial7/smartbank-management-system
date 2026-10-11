package com.smartbank.config;

import com.smartbank.entity.*;
import com.smartbank.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final TransactionLimitRepository transactionLimitRepository;
    private final BankCardRepository bankCardRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking SmartBank system initialization...");

        // Ensure transactions table column types align with JPA entity mappings
        alignTransactionSchema();

        // 1. Initialize Branches
        Branch branch1 = branchRepository.findByBranchCode("BR-001").orElseGet(() -> {
            Branch b = Branch.builder()
                    .branchCode("BR-001")
                    .branchName("SmartBank Main Branch")
                    .city("Metro City")
                    .address("100 Wall Street, Financial District")
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            return branchRepository.save(b);
        });

        Branch branch2 = branchRepository.findByBranchCode("BR-002").orElseGet(() -> {
            Branch b = Branch.builder()
                    .branchCode("BR-002")
                    .branchName("SmartBank North Branch")
                    .city("North City")
                    .address("250 North Boulevard, Tech Hub")
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            return branchRepository.save(b);
        });

        // 2. Initialize Default Transaction Limits
        initTransactionLimits();

        // 3. Initialize Staff & Role Users if not existing
        initStaffUsers(branch1);

        // 4. Initialize Sample Customers & Accounts if empty
        if (customerRepository.count() == 0) {
            initSampleCustomers(branch1);
        } else {
            // Ensure all existing customers have linked Account records and branch assignments
            syncAccountsForExistingCustomers(branch1);
        }

        // 5. Initialize Branch 2 Staff & Customer Data
        initBranch2Data(branch2);

        log.info("SmartBank database initialized with RBAC roles, branches (BR-001, BR-002), limits, and sample data.");
    }

    private void initTransactionLimits() {
        if (transactionLimitRepository.count() == 0) {
            transactionLimitRepository.save(TransactionLimit.builder()
                    .role(Role.CUSTOMER)
                    .singleTransactionLimit(new BigDecimal("25000.00"))
                    .dailyLimit(new BigDecimal("50000.00"))
                    .approvalThreshold(new BigDecimal("10000.00"))
                    .build());

            transactionLimitRepository.save(TransactionLimit.builder()
                    .role(Role.TELLER)
                    .singleTransactionLimit(new BigDecimal("100000.00"))
                    .dailyLimit(new BigDecimal("500000.00"))
                    .approvalThreshold(new BigDecimal("25000.00"))
                    .build());

            transactionLimitRepository.save(TransactionLimit.builder()
                    .role(Role.BRANCH_MANAGER)
                    .singleTransactionLimit(new BigDecimal("1000000.00"))
                    .dailyLimit(new BigDecimal("5000000.00"))
                    .approvalThreshold(new BigDecimal("500000.00"))
                    .build());
        }
    }

    private void initStaffUsers(Branch branch) {
        createUserIfAbsent("admin_a", "Admin@123", "admin@smartbank.com", "System Admin", Role.BANK_ADMIN, branch);
        createUserIfAbsent("superadmin", "SuperAdmin@123", "superadmin@smartbank.com", "Super Administrator", Role.SUPER_ADMIN, branch);
        createUserIfAbsent("manager_a", "Manager@123", "manager@smartbank.com", "Branch Manager A", Role.BRANCH_MANAGER, branch);
        createUserIfAbsent("teller_a", "Teller@123", "teller@smartbank.com", "Teller Officer A", Role.TELLER, branch);
        createUserIfAbsent("cso_a", "Cso@123", "cso@smartbank.com", "Customer Service Officer", Role.CUSTOMER_SERVICE_OFFICER, branch);
        createUserIfAbsent("compliance_a", "Compliance@123", "compliance@smartbank.com", "Chief Compliance Officer", Role.COMPLIANCE_OFFICER, branch);
        createUserIfAbsent("risk_a", "Risk@123", "risk@smartbank.com", "Risk Management Officer", Role.RISK_OFFICER, branch);
        createUserIfAbsent("auditor_a", "Auditor@123", "auditor@smartbank.com", "Internal Auditor A", Role.AUDITOR, branch);
        createUserIfAbsent("exec_a", "Exec@123", "exec@smartbank.com", "Executive Board Director", Role.EXECUTIVE, branch);
    }

    private User createUserIfAbsent(String username, String rawPassword, String email, String fullName, Role role, Branch branch) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .email(email)
                    .fullName(fullName)
                    .role(role)
                    .branch(branch)
                    .enabled(true)
                    .accountNonLocked(true)
                    .failedLoginAttempts(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            return userRepository.save(user);
        });
    }

    private void initSampleCustomers(Branch branch) {
        LocalDateTime accountCreatedDate = LocalDateTime.of(2026, 6, 18, 10, 30, 0);

        // Customer A: user "customer_a"
        User userA = createUserIfAbsent("customer_a", "Customer@123", "customer_a@smartbank.com", "Customer Alpha", Role.CUSTOMER, branch);
        Customer appu = Customer.builder()
                .name("Appu (Customer Alpha)")
                .email("appu@smartbank.com")
                .phone("9876543210")
                .accountNumber("SB3532006893")
                .balance(new BigDecimal("156000.00"))
                .user(userA)
                .branch(branch)
                .panNumber("ABCDE1234F")
                .itrFiler(true)
                .form60Submitted(false)
                .kycStatus(KycStatus.VERIFIED)
                .status(CustomerStatus.ACTIVE)
                .createdAt(accountCreatedDate)
                .updatedAt(accountCreatedDate)
                .build();
        Customer savedAppu = customerRepository.save(appu);
        createAccountForCustomer(savedAppu, branch);

        // Customer B: user "customer_b"
        User userB = createUserIfAbsent("customer_b", "Customer@123", "customer_b@smartbank.com", "Customer Beta", Role.CUSTOMER, branch);
        Customer gokul = Customer.builder()
                .name("Gokul Raj (Customer Beta)")
                .email("gokul@smartbank.com")
                .phone("9876543211")
                .accountNumber("SB7505485085")
                .balance(new BigDecimal("15000.00"))
                .user(userB)
                .branch(branch)
                .panNumber("FGHIJ5678K")
                .itrFiler(true)
                .form60Submitted(false)
                .kycStatus(KycStatus.VERIFIED)
                .status(CustomerStatus.ACTIVE)
                .createdAt(accountCreatedDate)
                .updatedAt(accountCreatedDate)
                .build();
        Customer savedGokul = customerRepository.save(gokul);
        createAccountForCustomer(savedGokul, branch);

        // Customer C: Tharun (No PAN on record, for Rule 114B compliance testing)
        Customer tharun = Customer.builder()
                .name("Tharun")
                .email("tharun@smartbank.com")
                .phone("9876543212")
                .accountNumber("SB2929788625")
                .balance(new BigDecimal("45000.00"))
                .branch(branch)
                .panNumber(null)
                .itrFiler(true)
                .form60Submitted(false)
                .kycStatus(KycStatus.VERIFIED)
                .status(CustomerStatus.ACTIVE)
                .createdAt(accountCreatedDate)
                .updatedAt(accountCreatedDate)
                .build();
        Customer savedTharun = customerRepository.save(tharun);
        createAccountForCustomer(savedTharun, branch);

        // Customer D: Shankar (Non-ITR Filer for Section 194N testing)
        Customer shankar = Customer.builder()
                .name("shankar")
                .email("shankar@smartbank.com")
                .phone("9876543213")
                .accountNumber("SB5748533077")
                .balance(new BigDecimal("100000.00"))
                .branch(branch)
                .panNumber("KLMNO9012P")
                .itrFiler(false)
                .form60Submitted(false)
                .kycStatus(KycStatus.VERIFIED)
                .status(CustomerStatus.ACTIVE)
                .createdAt(accountCreatedDate)
                .updatedAt(accountCreatedDate)
                .build();
        Customer savedShankar = customerRepository.save(shankar);
        createAccountForCustomer(savedShankar, branch);

        // Seed Sample Historical Transactions
        Transaction t1 = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("1000.00"))
                .receiverAccount(savedAppu)
                .balanceAfterTransaction(new BigDecimal("1000.00"))
                .description("Cash Deposit")
                .transactionDate(LocalDateTime.of(2026, 6, 19, 16, 15, 0))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t2 = Transaction.builder()
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .amount(new BigDecimal("200000.00"))
                .receiverAccount(savedAppu)
                .balanceAfterTransaction(new BigDecimal("201000.00"))
                .description("Cash Deposit")
                .transactionDate(LocalDateTime.of(2026, 6, 20, 9, 13, 0))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t3 = Transaction.builder()
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("50000.00"))
                .senderAccount(savedAppu)
                .balanceAfterTransaction(new BigDecimal("151000.00"))
                .description("withdrawal")
                .transactionDate(LocalDateTime.of(2026, 6, 20, 9, 17, 0))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t4 = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_SENT)
                .amount(new BigDecimal("5000.00"))
                .senderAccount(savedTharun)
                .receiverAccount(savedGokul)
                .balanceAfterTransaction(new BigDecimal("45000.00"))
                .description("Rent payment to Gokul Raj")
                .transactionDate(LocalDateTime.of(2026, 6, 20, 9, 37, 0))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        Transaction t5 = Transaction.builder()
                .transactionType(Transaction.TransactionType.TRANSFER_RECEIVED)
                .amount(new BigDecimal("5000.00"))
                .senderAccount(savedTharun)
                .receiverAccount(savedGokul)
                .balanceAfterTransaction(new BigDecimal("15000.00"))
                .description("Rent payment from Tharun")
                .transactionDate(LocalDateTime.of(2026, 6, 20, 9, 37, 0))
                .status(Transaction.TransactionStatus.SUCCESS)
                .build();

        transactionRepository.save(t1);
        transactionRepository.save(t2);
        transactionRepository.save(t3);
        transactionRepository.save(t4);
        transactionRepository.save(t5);
    }

    private void initBranch2Data(Branch branch2) {
        createUserIfAbsent("manager_b", "Manager@123", "manager_b@smartbank.com", "Branch Manager B", Role.BRANCH_MANAGER, branch2);
        createUserIfAbsent("teller_b", "Teller@123", "teller_b@smartbank.com", "Teller Officer B", Role.TELLER, branch2);

        if (customerRepository.findByAccountNumber("SB6621980011").isEmpty()) {
            User userPriya = createUserIfAbsent("customer_priya", "Customer@123", "priya@smartbank.com", "Priya Sharma", Role.CUSTOMER, branch2);
            Customer priya = Customer.builder()
                    .name("Priya Sharma (North Branch)")
                    .email("priya@smartbank.com")
                    .phone("9876543299")
                    .accountNumber("SB6621980011")
                    .balance(new BigDecimal("25000.00"))
                    .user(userPriya)
                    .branch(branch2)
                    .kycStatus(KycStatus.VERIFIED)
                    .status(CustomerStatus.ACTIVE)
                    .createdAt(LocalDateTime.of(2026, 7, 1, 11, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 7, 1, 11, 0, 0))
                    .build();
            Customer savedPriya = customerRepository.save(priya);
            createAccountForCustomer(savedPriya, branch2);
        }
    }

    private void syncAccountsForExistingCustomers(Branch branch) {
        customerRepository.findAll().forEach(cust -> {
            boolean modified = false;
            if (cust.getBranch() == null) {
                cust.setBranch(branch);
                modified = true;
            }
            if (cust.getStatus() == null) {
                cust.setStatus(CustomerStatus.ACTIVE);
                modified = true;
            }
            if (cust.getKycStatus() == null) {
                cust.setKycStatus(KycStatus.VERIFIED);
                modified = true;
            }
            if (modified) {
                customerRepository.save(cust);
            }
            if (accountRepository.findByAccountNumber(cust.getAccountNumber()).isEmpty()) {
                createAccountForCustomer(cust, cust.getBranch() != null ? cust.getBranch() : branch);
            }
        });
        // Ensure all existing accounts are configured with branch and INR currency
        accountRepository.findAll().forEach(acc -> {
            boolean modified = false;
            if (acc.getBranch() == null) {
                Branch b = acc.getCustomer() != null && acc.getCustomer().getBranch() != null
                        ? acc.getCustomer().getBranch() : branch;
                acc.setBranch(b);
                modified = true;
            }
            if ("USD".equalsIgnoreCase(acc.getCurrency())) {
                acc.setCurrency("INR");
                modified = true;
            }
            if (modified) {
                accountRepository.save(acc);
            }
        });
        // Ensure customer_a and customer_b exist
        User userA = createUserIfAbsent("customer_a", "Customer@123", "customer_a@smartbank.com", "Customer Alpha", Role.CUSTOMER, branch);
        User userB = createUserIfAbsent("customer_b", "Customer@123", "customer_b@smartbank.com", "Customer Beta", Role.CUSTOMER, branch);
        customerRepository.findByAccountNumber("SB3532006893").ifPresent(c -> {
            c.setUser(userA);
            customerRepository.save(c);
        });
        customerRepository.findByAccountNumber("SB7505485085").ifPresent(c -> {
            c.setUser(userB);
            customerRepository.save(c);
        });
    }

    private void createAccountForCustomer(Customer customer, Branch branch) {
        Account account = Account.builder()
                .accountNumber(customer.getAccountNumber())
                .customer(customer)
                .branch(branch)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(customer.getBalance())
                .ledgerBalance(customer.getBalance())
                .currency("INR")
                .createdAt(LocalDateTime.now())
                .build();
        Account savedAccount = accountRepository.save(account);

        // Seed a sample debit card
        String maskedCard = "4532-" + customer.getAccountNumber().substring(Math.max(0, customer.getAccountNumber().length() - 4)) + "-8899";
        BankCard card = BankCard.builder()
                .account(savedAccount)
                .cardNumber(maskedCard)
                .cardHolderName(customer.getName())
                .cardType("DEBIT")
                .expiryDate(LocalDate.now().plusYears(3))
                .status("ACTIVE")
                .dailyLimit(new BigDecimal("2000.00"))
                .build();
        bankCardRepository.save(card);
    }

    private void alignTransactionSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE transactions MODIFY COLUMN status VARCHAR(30) DEFAULT 'SUCCESS'");
            jdbcTemplate.execute("ALTER TABLE transactions MODIFY COLUMN transaction_type VARCHAR(30) NOT NULL");
            log.info("Verified transactions table schema: status and transaction_type aligned with JPA entity mappings.");
        } catch (Exception e) {
            log.debug("Schema alignment skipped or already VARCHAR: {}", e.getMessage());
        }
    }
}
