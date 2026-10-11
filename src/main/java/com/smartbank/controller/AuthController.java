package com.smartbank.controller;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.BranchRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.smartbank.service.AuditService auditService;

    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                @RequestParam(value = "registered", required = false) String registered,
                                @RequestParam(value = "message", required = false) String message,
                                Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", message != null ? message : "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out securely.");
        }
        if (registered != null && !model.containsAttribute("successMessage")) {
            model.addAttribute("successMessage", "Registration submitted! Your account is created in PENDING_VERIFICATION status with a ₹0.00 starting balance.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        return "register";
    }

    @PostMapping("/register")
    public String processRegistration(@RequestParam String username,
                                      @RequestParam String password,
                                      @RequestParam(required = false, defaultValue = "") String confirmPassword,
                                      @RequestParam String fullName,
                                      @RequestParam String email,
                                      @RequestParam String phone,
                                      @RequestParam(required = false) String address,
                                      @RequestParam(defaultValue = "false") boolean agreeTerms,
                                      jakarta.servlet.http.HttpServletRequest request,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            if (!agreeTerms) {
                throw new BankingException("You must agree to the Terms of Service and Banking Regulations to proceed.");
            }
            if (password == null || password.length() < 8) {
                throw new BankingException("Password must be at least 8 characters long.");
            }
            if (!password.equals(confirmPassword)) {
                throw new BankingException("Passwords do not match. Please re-enter.");
            }
            if (userRepository.existsByUsername(username)) {
                throw new BankingException("Username '" + username + "' is already taken.");
            }
            if (userRepository.existsByEmail(email) || customerRepository.existsByEmail(email)) {
                throw new BankingException("Email '" + email + "' is already registered.");
            }

            Branch branch = branchRepository.findAll().stream().findFirst().orElse(null);

            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .email(email)
                    .fullName(fullName)
                    .role(Role.CUSTOMER) // Strictly hardcoded to CUSTOMER - no privilege escalation
                    .branch(branch)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            User savedUser = userRepository.save(user);

            Customer customer = Customer.builder()
                    .name(fullName)
                    .email(email)
                    .phone(phone)
                    .address(address)
                    .user(savedUser)
                    .branch(branch)
                    .balance(BigDecimal.ZERO) // No simulated or fabricated money
                    .kycStatus(KycStatus.PENDING) // Regulatory requirement: pending KYC
                    .status(CustomerStatus.PENDING_VERIFICATION)
                    .build();

            Customer savedCustomer = customerService.createCustomer(customer);

            auditService.logEvent(
                    savedUser.getId(),
                    savedUser.getUsername(),
                    "CUSTOMER",
                    "CUSTOMER_REGISTRATION_REQUEST",
                    "CUSTOMER",
                    savedCustomer.getId().toString(),
                    "Customer registration submitted. Account: " + savedCustomer.getAccountNumber() + " [Status: PENDING_VERIFICATION, Balance: ₹0.00]",
                    auditService.extractClientIp(request),
                    "SUCCESS"
            );

            redirectAttributes.addFlashAttribute("successMessage",
                    "Account registered successfully! Account #: " + savedCustomer.getAccountNumber() + 
                    ". Note: Account is in PENDING_VERIFICATION status with a ₹0.00 balance. Please log in to complete verification.");
            return "redirect:/login?registered=true";
        } catch (BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("username", username);
            model.addAttribute("fullName", fullName);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            model.addAttribute("address", address);
            return "register";
        }
    }

    @RequestMapping("/access-denied")
    public String accessDenied(Model model) {
        if (!model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", "Access Denied: You do not have permission to view or execute this resource.");
        }
        return "access-denied";
    }
}
