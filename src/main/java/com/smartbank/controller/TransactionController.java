package com.smartbank.controller;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import com.smartbank.compliance.CompliancePaymentChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final AuthorizationService authorizationService;

    // ========== TRANSACTION HISTORY ==========

    @GetMapping("/transactions")
    public String transactionHistory(Model model) {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = authorizationService.getCurrentCustomer().orElse(null);
            List<com.smartbank.entity.Transaction> custTxns = (customer != null) ? transactionService.getTransactionsByCustomer(customer) : List.of();
            model.addAttribute("transactions", custTxns);
            model.addAttribute("totalTransactions", (long) custTxns.size());
            BigDecimal deposits = custTxns.stream()
                    .filter(t -> t.getTransactionType() == com.smartbank.entity.Transaction.TransactionType.DEPOSIT ||
                            t.getTransactionType() == com.smartbank.entity.Transaction.TransactionType.TRANSFER_RECEIVED)
                    .map(com.smartbank.entity.Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal withdrawals = custTxns.stream()
                    .filter(t -> t.getTransactionType() == com.smartbank.entity.Transaction.TransactionType.WITHDRAWAL ||
                            t.getTransactionType() == com.smartbank.entity.Transaction.TransactionType.TRANSFER_SENT)
                    .map(com.smartbank.entity.Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("totalDeposits", deposits);
            model.addAttribute("totalWithdrawals", withdrawals);
            return "transaction-history";
        }

        model.addAttribute("transactions", transactionService.getAllTransactions());
        model.addAttribute("totalTransactions", transactionService.countTotalTransactions());
        model.addAttribute("totalDeposits", transactionService.sumTotalDeposits());
        model.addAttribute("totalWithdrawals", transactionService.sumTotalWithdrawals());
        return "transaction-history";
    }

    // ========== DEPOSIT ==========

    @GetMapping("/deposit")
    public String showDepositForm(@RequestParam(required = false) String accountNumber, Model model) {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer currentCust = authorizationService.getCurrentCustomer().orElse(null);
            if (accountNumber == null || accountNumber.isBlank()) {
                accountNumber = (currentCust != null) ? currentCust.getAccountNumber() : "";
            } else if (currentCust != null && !accountNumber.equalsIgnoreCase(currentCust.getAccountNumber())) {
                log.warn("[AUTH-OWNERSHIP-DENIAL] Customer [{}] attempted to lookup account [{}] on deposit page",
                        currentUser.getUsername(), accountNumber);
                model.addAttribute("errorMessage", "Access Denied: You do not own account " + accountNumber + ". Customers can only deposit into their own accounts.");
                accountNumber = currentCust.getAccountNumber();
            }
        }

        model.addAttribute("accountNumber", accountNumber != null ? accountNumber : "");
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                model.addAttribute("customer", customerService.getCustomerByAccountNumber(accountNumber));
            } catch (AccessDeniedException e) {
                log.warn("[AUTH-SERVICE-DENIAL] Deposit form lookup denied for user [{}] and account [{}]: {}",
                        currentUser != null ? currentUser.getUsername() : "ANONYMOUS", accountNumber, e.getMessage());
                model.addAttribute("errorMessage", "Access Denied: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return "deposit";
    }

    @PostMapping("/deposit")
    public String processDeposit(@RequestParam String accountNumber,
                                  @RequestParam BigDecimal amount,
                                  @RequestParam(required = false) String description,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        try {
            customerService.deposit(accountNumber, amount, description);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully deposited ₹%.2f to account %s", amount, accountNumber));
            return "redirect:/customers/account/" + accountNumber;
        } catch (BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("accountNumber", accountNumber);
            return "deposit";
        }
    }

    // ========== WITHDRAW ==========

    @GetMapping("/withdraw")
    public String showWithdrawForm(@RequestParam(required = false) String accountNumber, Model model) {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer currentCust = authorizationService.getCurrentCustomer().orElse(null);
            if (accountNumber == null || accountNumber.isBlank()) {
                accountNumber = (currentCust != null) ? currentCust.getAccountNumber() : "";
            } else if (currentCust != null && !accountNumber.equalsIgnoreCase(currentCust.getAccountNumber())) {
                log.warn("[AUTH-OWNERSHIP-DENIAL] Customer [{}] attempted to lookup account [{}] on withdraw page",
                        currentUser.getUsername(), accountNumber);
                model.addAttribute("errorMessage", "Access Denied: You do not own account " + accountNumber + ". Customers can only withdraw from their own accounts.");
                accountNumber = currentCust.getAccountNumber();
            }
        }

        model.addAttribute("accountNumber", accountNumber != null ? accountNumber : "");
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                model.addAttribute("customer", customerService.getCustomerByAccountNumber(accountNumber));
            } catch (AccessDeniedException e) {
                log.warn("[AUTH-SERVICE-DENIAL] Withdraw form lookup denied for user [{}] and account [{}]: {}",
                        currentUser != null ? currentUser.getUsername() : "ANONYMOUS", accountNumber, e.getMessage());
                model.addAttribute("errorMessage", "Access Denied: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String processWithdrawal(@RequestParam String accountNumber,
                                     @RequestParam BigDecimal amount,
                                     @RequestParam(required = false) String description,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        try {
            customerService.withdraw(accountNumber, amount, description);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully withdrew ₹%.2f from account %s", amount, accountNumber));
            return "redirect:/customers/account/" + accountNumber;
        } catch (BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("accountNumber", accountNumber);
            try {
                model.addAttribute("customer", customerService.getCustomerByAccountNumber(accountNumber));
            } catch (Exception ignored) {}
            return "withdraw";
        }
    }

    // ========== TRANSFER ==========

    @GetMapping("/transfer")
    public String showTransferForm(@RequestParam(required = false) String accountNumber, Model model) {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer currentCust = authorizationService.getCurrentCustomer().orElse(null);
            if (accountNumber == null || accountNumber.isBlank()) {
                accountNumber = (currentCust != null) ? currentCust.getAccountNumber() : "";
            } else if (currentCust != null && !accountNumber.equalsIgnoreCase(currentCust.getAccountNumber())) {
                log.warn("[AUTH-OWNERSHIP-DENIAL] Customer [{}] attempted to lookup account [{}] on transfer page",
                        currentUser.getUsername(), accountNumber);
                model.addAttribute("errorMessage", "Access Denied: You do not own account " + accountNumber + ". Transfers must originate from your own account.");
                accountNumber = currentCust.getAccountNumber();
            }
        }

        model.addAttribute("senderAccount", accountNumber != null ? accountNumber : "");
        model.addAttribute("channels", CompliancePaymentChannel.values());
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                model.addAttribute("sender", customerService.getCustomerByAccountNumber(accountNumber));
            } catch (AccessDeniedException e) {
                log.warn("[AUTH-SERVICE-DENIAL] Transfer form lookup denied for user [{}] and account [{}]: {}",
                        currentUser != null ? currentUser.getUsername() : "ANONYMOUS", accountNumber, e.getMessage());
                model.addAttribute("errorMessage", "Access Denied: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return "transfer";
    }

    @PostMapping("/transfer")
    public String processTransfer(@RequestParam String senderAccount,
                                   @RequestParam String receiverAccount,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false, defaultValue = "INTERNAL") CompliancePaymentChannel channel,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        try {
            customerService.transfer(senderAccount, receiverAccount, amount, description, channel);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully transferred ₹%.2f via %s from account %s to %s",
                            amount, channel.getDisplayName(), senderAccount, receiverAccount));
            return "redirect:/customers/account/" + senderAccount;
        } catch (BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("senderAccount", senderAccount);
            model.addAttribute("receiverAccount", receiverAccount);
            model.addAttribute("channels", CompliancePaymentChannel.values());
            model.addAttribute("selectedChannel", channel);
            try {
                model.addAttribute("sender", customerService.getCustomerByAccountNumber(senderAccount));
            } catch (Exception ignored) {}
            return "transfer";
        }
    }
}
