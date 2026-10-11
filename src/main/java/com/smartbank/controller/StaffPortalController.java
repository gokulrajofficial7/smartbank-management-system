package com.smartbank.controller;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import com.smartbank.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffPortalController {

    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AuthorizationService authorizationService;
    private final com.smartbank.repository.KycDocumentRepository kycDocumentRepository;
    private final com.smartbank.service.AuditService auditService;

    @GetMapping("/teller")
    public String tellerDashboard(Model model) {
        User staff = authorizationService.getRequiredCurrentUser();
        model.addAttribute("staff", staff);
        model.addAttribute("recentTransactions", transactionService.getRecentTransactions());
        return "staff/teller";
    }

    @PostMapping("/deposit")
    public String processTellerDeposit(@RequestParam String accountNumber,
                                       @RequestParam BigDecimal amount,
                                       @RequestParam(required = false) String description,
                                       RedirectAttributes redirectAttributes) {
        try {
            customerService.deposit(accountNumber, amount, description);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Cash deposit of %s to account %s completed successfully.", CurrencyUtil.format(amount), accountNumber));
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/teller";
    }

    @PostMapping("/withdraw")
    public String processTellerWithdrawal(@RequestParam String accountNumber,
                                          @RequestParam BigDecimal amount,
                                          @RequestParam(required = false) String description,
                                          RedirectAttributes redirectAttributes) {
        try {
            customerService.withdraw(accountNumber, amount, description);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Cash withdrawal of %s from account %s completed successfully.", CurrencyUtil.format(amount), accountNumber));
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/staff/teller";
    }

    @GetMapping("/cso")
    public String csoDashboard(Model model) {
        User staff = authorizationService.getRequiredCurrentUser();
        List<Customer> branchCustomers;
        if (staff.getBranch() != null) {
            branchCustomers = customerRepository.findByBranchId(staff.getBranch().getId());
        } else {
            branchCustomers = customerRepository.findAll();
        }
        List<KycDocument> pendingKycDocs = kycDocumentRepository.findByStatus(KycStatus.PENDING);

        model.addAttribute("staff", staff);
        model.addAttribute("customers", branchCustomers);
        model.addAttribute("pendingKycDocs", pendingKycDocs);
        return "staff/cso";
    }

    @PostMapping("/cso/verify-kyc")
    public String verifyKyc(@RequestParam Long documentId,
                            @RequestParam(defaultValue = "APPROVED") String decision,
                            @RequestParam(required = false) String remarks,
                            RedirectAttributes redirectAttributes) {
        User staff = authorizationService.getRequiredCurrentUser();
        KycDocument doc = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BankingException("KYC document not found: " + documentId));

        if ("APPROVED".equalsIgnoreCase(decision)) {
            doc.setStatus(KycStatus.VERIFIED);
            doc.setVerifiedBy(staff);
            doc.setVerifiedAt(java.time.LocalDateTime.now());
            doc.setRemarks(remarks);
            kycDocumentRepository.save(doc);

            Customer customer = doc.getCustomer();
            customer.setKycStatus(KycStatus.VERIFIED);
            customer.setStatus(CustomerStatus.ACTIVE);
            customerRepository.save(customer);

            // Activate customer's account from DORMANT to ACTIVE
            accountRepository.findByAccountNumber(customer.getAccountNumber()).ifPresent(acc -> {
                if (acc.getStatus() == AccountStatus.DORMANT) {
                    acc.setStatus(AccountStatus.ACTIVE);
                    accountRepository.save(acc);
                }
            });

            auditService.logEvent(
                    staff.getId(),
                    staff.getUsername(),
                    staff.getRole().name(),
                    "KYC_VERIFICATION_APPROVED",
                    "CUSTOMER",
                    customer.getId().toString(),
                    "Verified KYC for customer " + customer.getName() + " [doc: " + doc.getDocumentType() + "]",
                    "127.0.0.1",
                    "SUCCESS"
            );

            redirectAttributes.addFlashAttribute("successMessage",
                    "KYC verified and customer account activated for " + customer.getName());
        } else {
            doc.setStatus(KycStatus.REJECTED);
            doc.setVerifiedBy(staff);
            doc.setVerifiedAt(java.time.LocalDateTime.now());
            doc.setRemarks(remarks);
            kycDocumentRepository.save(doc);

            Customer customer = doc.getCustomer();
            customer.setKycStatus(KycStatus.REJECTED);
            customerRepository.save(customer);

            auditService.logEvent(
                    staff.getId(),
                    staff.getUsername(),
                    staff.getRole().name(),
                    "KYC_VERIFICATION_REJECTED",
                    "CUSTOMER",
                    customer.getId().toString(),
                    "Rejected KYC for customer " + customer.getName() + " [Reason: " + remarks + "]",
                    "127.0.0.1",
                    "SUCCESS"
            );

            redirectAttributes.addFlashAttribute("errorMessage",
                    "KYC rejected for customer " + customer.getName());
        }

        return "redirect:/staff/cso";
    }
}
