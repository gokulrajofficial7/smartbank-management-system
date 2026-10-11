package com.smartbank.controller;

import com.smartbank.entity.Role;
import com.smartbank.entity.TransactionLimit;
import com.smartbank.entity.User;
import com.smartbank.repository.TransactionLimitRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.security.AuthorizationService;
import com.smartbank.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminPortalController {

    private final UserRepository userRepository;
    private final TransactionLimitRepository transactionLimitRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        User admin = authorizationService.getRequiredCurrentUser();
        List<User> users = userRepository.findAll();
        List<TransactionLimit> limits = transactionLimitRepository.findAll();

        model.addAttribute("admin", admin);
        model.addAttribute("users", users);
        model.addAttribute("limits", limits);
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        User admin = authorizationService.getRequiredCurrentUser();
        model.addAttribute("admin", admin);
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle-lock")
    public String toggleUserLock(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User admin = authorizationService.getRequiredCurrentUser();
        userRepository.findById(id).ifPresent(u -> {
            boolean currentLocked = Boolean.FALSE.equals(u.getAccountNonLocked());
            u.setAccountNonLocked(currentLocked); // flip
            u.setFailedLoginAttempts(0);
            u.setLockoutTime(null);
            userRepository.save(u);

            auditService.logEvent(
                    admin.getId(),
                    admin.getUsername(),
                    admin.getRole().name(),
                    "USER_LOCK_TOGGLE",
                    "USER",
                    id.toString(),
                    (currentLocked ? "Unlocked" : "Locked") + " user account: " + u.getUsername(),
                    "SYSTEM",
                    "SUCCESS"
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "User " + u.getUsername() + " status updated successfully.");
        });
        return "redirect:/admin/users";
    }

    @PostMapping("/limits")
    public String updateLimits(@RequestParam Role role,
                               @RequestParam BigDecimal singleLimit,
                               @RequestParam BigDecimal dailyLimit,
                               @RequestParam BigDecimal approvalThreshold,
                               RedirectAttributes redirectAttributes) {
        User admin = authorizationService.getRequiredCurrentUser();
        TransactionLimit limit = transactionLimitRepository.findByRole(role)
                .orElse(TransactionLimit.builder().role(role).build());

        limit.setSingleTransactionLimit(singleLimit);
        limit.setDailyLimit(dailyLimit);
        limit.setApprovalThreshold(approvalThreshold);
        transactionLimitRepository.save(limit);

        auditService.logEvent(
                admin.getId(),
                admin.getUsername(),
                admin.getRole().name(),
                "UPDATE_LIMITS",
                "TRANSACTION_LIMIT",
                role.name(),
                "Updated limits for role " + role + ": Single=" + singleLimit + ", Daily=" + dailyLimit + ", Threshold=" + approvalThreshold,
                "SYSTEM",
                "SUCCESS"
        );

        redirectAttributes.addFlashAttribute("successMessage", "Transaction limits updated for role: " + role);
        return "redirect:/admin/dashboard";
    }
}
