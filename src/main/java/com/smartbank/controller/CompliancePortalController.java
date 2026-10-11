package com.smartbank.controller;

import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import com.smartbank.entity.AuditLog;
import com.smartbank.entity.User;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.AccountService;
import com.smartbank.service.AuditService;
import com.smartbank.service.LedgerService;
import com.smartbank.compliance.IndianBankingComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/compliance")
@RequiredArgsConstructor
@Slf4j
public class CompliancePortalController {

    private final AuditService auditService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final AuthorizationService authorizationService;
    private final IndianBankingComplianceService complianceService;

    @GetMapping("/dashboard")
    public String complianceDashboard(Model model) {
        User officer = authorizationService.getRequiredCurrentUser();
        List<AuditLog> violations = auditService.getSecurityViolations();
        List<Account> frozenAccounts = accountRepository.findByStatus(AccountStatus.FROZEN);
        boolean ledgerBalanced = ledgerService.verifyLedgerIntegrity();

        model.addAttribute("officer", officer);
        model.addAttribute("violations", violations);
        model.addAttribute("frozenAccounts", frozenAccounts);
        model.addAttribute("ledgerBalanced", ledgerBalanced);
        model.addAttribute("rules", complianceService.getAllComplianceRules());
        model.addAttribute("complaints", complianceService.getAllComplaints());
        return "compliance/dashboard";
    }

    @GetMapping("/rules")
    public String viewRules(Model model) {
        User officer = authorizationService.getRequiredCurrentUser();
        model.addAttribute("officer", officer);
        model.addAttribute("rules", complianceService.getAllComplianceRules());
        return "compliance/rules";
    }

    @GetMapping("/complaints")
    public String viewComplaints(Model model) {
        User officer = authorizationService.getRequiredCurrentUser();
        model.addAttribute("officer", officer);
        model.addAttribute("complaints", complianceService.getAllComplaints());
        return "compliance/complaints";
    }

    @GetMapping("/audit-logs")
    public String viewAuditLogs(Model model) {
        User officer = authorizationService.getRequiredCurrentUser();
        List<AuditLog> auditLogs = auditService.getRecentAuditLogs();
        model.addAttribute("officer", officer);
        model.addAttribute("auditLogs", auditLogs);
        return "compliance/audit-logs";
    }

    @PostMapping("/freeze")
    public String freezeAccount(@RequestParam String accountNumber,
                                @RequestParam String reason,
                                RedirectAttributes redirectAttributes) {
        try {
            accountService.freezeAccount(accountNumber, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Account " + accountNumber + " has been FROZEN.");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/compliance/dashboard";
    }

    @PostMapping("/unfreeze")
    public String unfreezeAccount(@RequestParam String accountNumber,
                                  @RequestParam String reason,
                                  RedirectAttributes redirectAttributes) {
        try {
            accountService.unfreezeAccount(accountNumber, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Account " + accountNumber + " has been UN-FROZEN.");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/compliance/dashboard";
    }
}
