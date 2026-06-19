package com.smartbank.controller;

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
