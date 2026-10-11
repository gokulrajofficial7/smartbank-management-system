package com.smartbank.controller;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.BankCardRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.KycDocumentRepository;
import com.smartbank.repository.LoanApplicationRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.AccountService;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import com.smartbank.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import com.smartbank.compliance.IndianBankingComplianceService;
import com.smartbank.compliance.CompliancePaymentChannel;
import com.smartbank.compliance.UnauthorizedTransactionComplaint;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/customer")
@RequiredArgsConstructor
@Slf4j
public class CustomerPortalController {

    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final AuthorizationService authorizationService;
    private final AccountRepository accountRepository;
    private final BankCardRepository bankCardRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final com.smartbank.service.AuditService auditService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final IndianBankingComplianceService complianceService;

    @GetMapping("/dashboard")
    public String customerDashboard(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile linked to your user account"));

        List<Account> accounts = accountRepository.findByCustomerId(customer.getId());
        List<Transaction> transactions = transactionService.getTransactionsByCustomer(customer);

        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        model.addAttribute("transactions", transactions);
        model.addAttribute("beneficiaries", accountService.getMyBeneficiaries());
        return "customer/dashboard";
    }

    @GetMapping("/transfer")
    public String showTransferPage(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));

        List<Account> accounts = accountRepository.findByCustomerId(customer.getId());
        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        model.addAttribute("beneficiaries", accountService.getMyBeneficiaries());
        model.addAttribute("channels", CompliancePaymentChannel.values());
        return "customer/transfer";
    }

    @PostMapping("/transfer")
    public String processCustomerTransfer(@RequestParam String senderAccountNumber,
                                          @RequestParam String receiverAccountNumber,
                                          @RequestParam BigDecimal amount,
                                          @RequestParam(required = false) String description,
                                          @RequestParam(required = false, defaultValue = "INTERNAL") CompliancePaymentChannel channel,
                                          RedirectAttributes redirectAttributes,
                                          Model model) {
        try {
            // Strict ownership verification: sender account must belong to logged-in customer!
            authorizationService.verifyAccountAccess(senderAccountNumber);

            customerService.transfer(senderAccountNumber, receiverAccountNumber, amount, description, channel);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Transfer of %s via %s to %s processed successfully!",
                            CurrencyUtil.format(amount), channel.getDisplayName(), receiverAccountNumber));
            return "redirect:/customer/dashboard";
        } catch (BankingException e) {
            Customer customer = authorizationService.getCurrentCustomer().orElse(null);
            model.addAttribute("customer", customer);
            model.addAttribute("accounts", customer != null ? accountRepository.findByCustomerId(customer.getId()) : List.of());
            model.addAttribute("beneficiaries", accountService.getMyBeneficiaries());
            model.addAttribute("channels", CompliancePaymentChannel.values());
            model.addAttribute("selectedChannel", channel);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("senderAccountNumber", senderAccountNumber);
            model.addAttribute("receiverAccountNumber", receiverAccountNumber);
            model.addAttribute("amount", amount);
            return "customer/transfer";
        }
    }

    @GetMapping("/beneficiaries")
    public String listBeneficiaries(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        model.addAttribute("customer", customer);
        model.addAttribute("beneficiaries", accountService.getMyBeneficiaries());
        return "customer/beneficiaries";
    }

    @PostMapping("/beneficiaries")
    public String addBeneficiary(@RequestParam String targetAccountNumber,
                                 @RequestParam String targetAccountName,
                                 @RequestParam(required = false) String bankName,
                                 @RequestParam(required = false) String nickname,
                                 RedirectAttributes redirectAttributes) {
        try {
            accountService.addBeneficiary(targetAccountNumber, targetAccountName, bankName, nickname);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Beneficiary added successfully! A 4-hour security cooling-off period applies.");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error adding beneficiary", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to add beneficiary: " + e.getMessage());
        }
        return "redirect:/customer/beneficiaries";
    }

    @PostMapping("/beneficiaries/{id}/delete")
    public String deleteBeneficiary(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            accountService.deleteBeneficiary(id);
            redirectAttributes.addFlashAttribute("successMessage", "Beneficiary removed successfully.");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting beneficiary {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to remove beneficiary: " + e.getMessage());
        }
        return "redirect:/customer/beneficiaries";
    }

    @GetMapping("/cards")
    public String viewCards(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        List<Account> accounts = accountRepository.findByCustomerId(customer.getId());
        List<BankCard> cards = accounts.stream()
                .flatMap(acc -> bankCardRepository.findByAccountId(acc.getId()).stream())
                .toList();

        model.addAttribute("customer", customer);
        model.addAttribute("cards", cards);
        return "customer/cards";
    }

    @GetMapping("/loans")
    public String viewLoans(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        List<LoanApplication> loans = loanApplicationRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
        model.addAttribute("customer", customer);
        model.addAttribute("loans", loans);
        return "customer/loans";
    }

    @PostMapping("/loans")
    public String applyLoan(@RequestParam BigDecimal amount,
                            @RequestParam int termMonths,
                            @RequestParam String purpose,
                            RedirectAttributes redirectAttributes) {
        try {
            accountService.applyLoan(amount, termMonths, purpose);
            redirectAttributes.addFlashAttribute("successMessage", "Loan application submitted for review.");
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/loans";
    }

    @GetMapping("/profile")
    public String viewProfile(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        User user = authorizationService.getRequiredCurrentUser();
        List<KycDocument> kycDocs = kycDocumentRepository.findByCustomerId(customer.getId());

        model.addAttribute("customer", customer);
        model.addAttribute("user", user);
        model.addAttribute("kycDocs", kycDocs);
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String phone,
                                @RequestParam(required = false) String address,
                                RedirectAttributes redirectAttributes) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        customer.setPhone(phone);
        customer.setAddress(address);
        customerRepository.save(customer);

        auditService.logEvent(
                customer.getUser().getId(),
                customer.getUser().getUsername(),
                "CUSTOMER",
                "UPDATE_PROFILE",
                "CUSTOMER",
                customer.getId().toString(),
                "Customer updated contact details: phone=" + phone,
                "127.0.0.1",
                "SUCCESS"
        );

        redirectAttributes.addFlashAttribute("successMessage", "Profile contact details updated successfully.");
        return "redirect:/customer/profile";
    }

    @GetMapping("/accounts")
    public String viewAccounts(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        List<Account> accounts = accountRepository.findByCustomerId(customer.getId());
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        model.addAttribute("totalBalance", totalBalance);
        return "customer/accounts";
    }

    @GetMapping("/transactions")
    public String viewTransactions(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        List<Transaction> transactions = transactionService.getTransactionsByCustomer(customer);

        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactions);
        return "customer/transactions";
    }

    @GetMapping("/security")
    public String viewSecurity(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        User user = authorizationService.getRequiredCurrentUser();

        model.addAttribute("customer", customer);
        model.addAttribute("user", user);
        return "customer/security";
    }

    @PostMapping("/security/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmNewPassword,
                                 RedirectAttributes redirectAttributes) {
        User user = authorizationService.getRequiredCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Current password does not match.");
            return "redirect:/customer/security";
        }
        if (newPassword == null || newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("errorMessage", "New password must be at least 8 characters long.");
            return "redirect:/customer/security";
        }
        if (!newPassword.equals(confirmNewPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "New passwords do not match.");
            return "redirect:/customer/security";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditService.logEvent(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                "CHANGE_PASSWORD",
                "USER",
                user.getId().toString(),
                "User successfully changed their password",
                "127.0.0.1",
                "SUCCESS"
        );

        redirectAttributes.addFlashAttribute("successMessage", "Password updated successfully!");
        return "redirect:/customer/security";
    }

    @PostMapping("/kyc")
    public String submitKyc(@RequestParam String documentType,
                            @RequestParam String documentNumber,
                            RedirectAttributes redirectAttributes) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));

        KycDocument doc = KycDocument.builder()
                .customer(customer)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .status(KycStatus.PENDING)
                .build();
        kycDocumentRepository.save(doc);

        auditService.logEvent(
                customer.getUser().getId(),
                customer.getUser().getUsername(),
                "CUSTOMER",
                "KYC_DOCUMENT_SUBMITTED",
                "KYC_DOCUMENT",
                doc.getId().toString(),
                "Customer submitted KYC document: " + documentType + " (" + documentNumber + ")",
                "127.0.0.1",
                "SUCCESS"
        );

        redirectAttributes.addFlashAttribute("successMessage",
                "KYC identity document (" + documentType + ") submitted for verification. Bank compliance will review your submission.");
        return "redirect:/customer/profile";
    }

    @PostMapping("/profile/pan")
    public String updatePan(@RequestParam(required = false) String panNumber,
                            @RequestParam(required = false) Boolean form60Submitted,
                            RedirectAttributes redirectAttributes) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        if (panNumber != null && !panNumber.isBlank()) {
            customer.setPanNumber(panNumber.trim().toUpperCase());
            customer.setForm60Submitted(false);
        } else if (Boolean.TRUE.equals(form60Submitted)) {
            customer.setForm60Submitted(true);
        }
        customerRepository.save(customer);
        redirectAttributes.addFlashAttribute("successMessage", "Tax compliance details updated successfully (Income Tax Rule 114B).");
        return "redirect:/customer/profile";
    }

    @GetMapping("/complaints")
    public String viewComplaints(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        List<UnauthorizedTransactionComplaint> complaints = complianceService.getCustomerComplaints(customer);
        model.addAttribute("customer", customer);
        model.addAttribute("complaints", complaints);
        return "customer/complaints";
    }

    @PostMapping("/complaints/report")
    public String fileComplaint(@RequestParam(required = false) String transactionReference,
                                @RequestParam BigDecimal disputeAmount,
                                @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime incidentDate,
                                @RequestParam String customerDescription,
                                RedirectAttributes redirectAttributes) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        try {
            UnauthorizedTransactionComplaint complaint = complianceService.fileUnauthorizedTransactionComplaint(
                    customer,
                    transactionReference,
                    disputeAmount,
                    incidentDate,
                    java.time.LocalDateTime.now(),
                    customerDescription
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Complaint registered successfully under Reference " + complaint.getComplaintReference() +
                    " (" + complaint.getLiabilityTier() + "). RBI shadow reversal deadline: " + complaint.getShadowReversalDeadline());
        } catch (BankingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customer/complaints";
    }

    @GetMapping("/positive-pay")
    public String viewPositivePay(Model model) {
        Customer customer = authorizationService.getCurrentCustomer()
                .orElseThrow(() -> new AccessDeniedException("No customer profile found"));
        List<Account> accounts = accountRepository.findByCustomerId(customer.getId());
        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        return "customer/positive-pay";
    }

    @PostMapping("/positive-pay")
    public String submitPositivePay(@RequestParam String chequeNumber,
                                    @RequestParam String payeeName,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam String accountNumber,
                                    RedirectAttributes redirectAttributes) {
        authorizationService.verifyAccountAccess(accountNumber);
        redirectAttributes.addFlashAttribute("successMessage",
                "Positive Pay confirmation recorded for Cheque #" + chequeNumber +
                " payable to " + payeeName + " for " + CurrencyUtil.format(amount) +
                ". CTS validation profile active.");
        return "redirect:/customer/positive-pay";
    }

    @GetMapping("/compliance-rules")
    public String viewComplianceRules(Model model) {
        Customer customer = authorizationService.getCurrentCustomer().orElse(null);
        model.addAttribute("customer", customer);
        model.addAttribute("rules", complianceService.getAllComplianceRules());
        return "customer/compliance-rules";
    }
}
