package com.smartbank.controller;

import com.smartbank.entity.Role;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final AuthorizationService authorizationService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("totalCustomers", customerService.countTotalCustomers());
        model.addAttribute("totalBalance", customerService.sumTotalBalance());
        model.addAttribute("totalTransactions", transactionService.countTotalTransactions());
        model.addAttribute("recentTransactions", transactionService.getRecentTransactions());
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Enforce role isolation: Do not expose global bank totals to ordinary customers
        if (authorizationService.getCurrentUser().isPresent()
                && authorizationService.getCurrentUser().get().getRole() == Role.CUSTOMER) {
            return "redirect:/customer/dashboard";
        }

        model.addAttribute("totalCustomers", customerService.countTotalCustomers());
        model.addAttribute("totalBalance", customerService.sumTotalBalance());
        model.addAttribute("totalTransactions", transactionService.countTotalTransactions());
        model.addAttribute("totalDeposits", transactionService.sumTotalDeposits());
        model.addAttribute("totalWithdrawals", transactionService.sumTotalWithdrawals());
        model.addAttribute("recentTransactions", transactionService.getRecentTransactions());
        model.addAttribute("allCustomers", customerService.getAllCustomers());
        return "dashboard";
    }
}
