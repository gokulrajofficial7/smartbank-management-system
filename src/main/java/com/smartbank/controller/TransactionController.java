package com.smartbank.controller;

import com.smartbank.exception.BankingException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class TransactionController {

    private final CustomerService customerService;
    private final TransactionService transactionService;

    // ========== TRANSACTION HISTORY ==========

    @GetMapping("/transactions")
    public String transactionHistory(Model model) {
        model.addAttribute("transactions", transactionService.getAllTransactions());
        model.addAttribute("totalTransactions", transactionService.countTotalTransactions());
        model.addAttribute("totalDeposits", transactionService.sumTotalDeposits());
        model.addAttribute("totalWithdrawals", transactionService.sumTotalWithdrawals());
        return "transaction-history";
    }

    // ========== DEPOSIT ==========

    @GetMapping("/deposit")
    public String showDepositForm(@RequestParam(required = false) String accountNumber, Model model) {
        model.addAttribute("accountNumber", accountNumber != null ? accountNumber : "");
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                model.addAttribute("customer", customerService.getCustomerByAccountNumber(accountNumber));
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
        model.addAttribute("accountNumber", accountNumber != null ? accountNumber : "");
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                model.addAttribute("customer", customerService.getCustomerByAccountNumber(accountNumber));
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
        } catch (InsufficientFundsException | BankingException e) {
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
        model.addAttribute("senderAccount", accountNumber != null ? accountNumber : "");
        if (accountNumber != null && !accountNumber.isBlank()) {
            try {
                model.addAttribute("sender", customerService.getCustomerByAccountNumber(accountNumber));
            } catch (Exception ignored) {}
        }
        return "transfer";
    }

    @PostMapping("/transfer")
    public String processTransfer(@RequestParam String senderAccount,
                                   @RequestParam String receiverAccount,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String description,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {
        try {
            customerService.transfer(senderAccount, receiverAccount, amount, description);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Successfully transferred ₹%.2f from account %s to %s", amount, senderAccount, receiverAccount));
            return "redirect:/customers/account/" + senderAccount;
        } catch (InsufficientFundsException | BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("senderAccount", senderAccount);
            model.addAttribute("receiverAccount", receiverAccount);
            try {
                model.addAttribute("sender", customerService.getCustomerByAccountNumber(senderAccount));
            } catch (Exception ignored) {}
            return "transfer";
        }
    }
}
