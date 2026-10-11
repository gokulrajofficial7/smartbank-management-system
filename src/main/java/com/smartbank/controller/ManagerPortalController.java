package com.smartbank.controller;

import com.smartbank.dto.BranchCustomerDto;
import com.smartbank.entity.Account;
import com.smartbank.entity.ApprovalRequest;
import com.smartbank.entity.Customer;
import com.smartbank.entity.Role;
import com.smartbank.entity.Transaction;
import com.smartbank.entity.User;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.ApprovalService;
import com.smartbank.service.AuditService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
@Slf4j
public class ManagerPortalController {

    private final ApprovalService approvalService;
    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final AuthorizationService authorizationService;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    @GetMapping("/dashboard")
    public String managerDashboard(Model model) {
        User manager = authorizationService.getRequiredCurrentUser();
        List<ApprovalRequest> pendingApprovals = approvalService.getAllPendingRequests();

        long branchCustomerCount;
        BigDecimal branchBalance;
        if (manager.getRole() == Role.BRANCH_MANAGER && manager.getBranch() != null) {
            branchCustomerCount = customerService.countCustomersForBranch(manager.getBranch().getId());
            branchBalance = customerService.sumBalanceForBranch(manager.getBranch().getId());
        } else {
            branchCustomerCount = customerService.countTotalCustomers();
            branchBalance = customerService.sumTotalBalance();
        }

        model.addAttribute("manager", manager);
        model.addAttribute("branch", manager.getBranch());
        model.addAttribute("pendingApprovals", pendingApprovals);
        model.addAttribute("totalCustomers", branchCustomerCount);
        model.addAttribute("totalBalance", branchBalance);
        model.addAttribute("totalTransactions", transactionService.countTotalTransactions());
        return "manager/dashboard";
    }

    @GetMapping("/customers")
    public String branchCustomers(@RequestParam(required = false) String query,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  Model model,
                                  HttpServletRequest request) {
        User manager = authorizationService.getRequiredCurrentUser();

        if (page < 0) page = 0;
        if (size <= 0 || size > 50) size = 10;

        Page<BranchCustomerDto> customersPage = customerService.getBranchCustomers(
                manager, query, PageRequest.of(page, size)
        );

        String branchIdentifier = (manager.getBranch() != null) ? manager.getBranch().getBranchCode() : "ALL";
        auditService.logEvent(
                manager.getId(),
                manager.getUsername(),
                manager.getRole().name(),
                "VIEW_BRANCH_CUSTOMERS",
                "BRANCH",
                branchIdentifier,
                "Branch Manager " + manager.getUsername() + " accessed customer list for branch " + branchIdentifier + (query != null ? " (query: " + query + ")" : ""),
                auditService.extractClientIp(request),
                "SUCCESS"
        );

        model.addAttribute("manager", manager);
        model.addAttribute("branch", manager.getBranch());
        model.addAttribute("customersPage", customersPage);
        model.addAttribute("query", query != null ? query.trim() : "");
        model.addAttribute("totalCount", customersPage.getTotalElements());

        return "manager/customers";
    }

    @GetMapping("/customers/{id}")
    public String viewBranchCustomerDetails(@PathVariable Long id,
                                            Model model,
                                            HttpServletRequest request) {
        User manager = authorizationService.getRequiredCurrentUser();

        // Strict branch authorization check (throws AccessDeniedException if cross-branch access)
        Customer customer = customerService.getBranchCustomerById(id, manager);

        auditService.logEvent(
                manager.getId(),
                manager.getUsername(),
                manager.getRole().name(),
                "VIEW_CUSTOMER_DETAILS",
                "CUSTOMER",
                id.toString(),
                "Branch Manager " + manager.getUsername() + " accessed customer details for " + customer.getName() + " [Branch: " + (customer.getBranch() != null ? customer.getBranch().getBranchCode() : "N/A") + "]",
                auditService.extractClientIp(request),
                "SUCCESS"
        );

        List<Account> accounts = accountRepository.findByCustomerId(customer.getId());
        List<Transaction> transactions = transactionService.getTransactionsByCustomer(customer);

        model.addAttribute("manager", manager);
        model.addAttribute("branch", manager.getBranch());
        model.addAttribute("customer", customer);
        model.addAttribute("maskedAccountNumber", BranchCustomerDto.maskAccountNumber(customer.getAccountNumber()));
        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);

        return "manager/customer-details";
    }

    @GetMapping("/approvals")
    public String approvalsQueue(Model model) {
        User manager = authorizationService.getRequiredCurrentUser();
        List<ApprovalRequest> pendingApprovals = approvalService.getAllPendingRequests();
        model.addAttribute("manager", manager);
        model.addAttribute("pendingApprovals", pendingApprovals);
        return "manager/approvals";
    }

    @PostMapping("/approvals/{id}/approve")
    public String approveTransaction(@PathVariable Long id,
                                     @RequestParam(required = false) String checkerNotes,
                                     RedirectAttributes redirectAttributes) {
        User checker = authorizationService.getRequiredCurrentUser();
        try {
            approvalService.approveRequest(id, checker, checkerNotes != null ? checkerNotes : "Approved by Branch Manager");
            redirectAttributes.addFlashAttribute("successMessage", "Request #" + id + " approved and transaction executed successfully!");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/manager/approvals";
    }

    @PostMapping("/approvals/{id}/reject")
    public String rejectTransaction(@PathVariable Long id,
                                    @RequestParam(required = false) String rejectionReason,
                                    RedirectAttributes redirectAttributes) {
        User checker = authorizationService.getRequiredCurrentUser();
        try {
            approvalService.rejectRequest(id, checker, rejectionReason != null ? rejectionReason : "Rejected by Branch Manager");
            redirectAttributes.addFlashAttribute("successMessage", "Request #" + id + " has been rejected.");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/manager/approvals";
    }
}
