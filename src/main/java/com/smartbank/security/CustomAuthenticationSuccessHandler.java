package com.smartbank.security;

import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // Reset failed login attempts and update last login
        userRepository.findById(user.getId()).ifPresent(u -> {
            u.setFailedLoginAttempts(0);
            u.setLockoutTime(null);
            u.setLastLoginAt(LocalDateTime.now());
            userRepository.save(u);
        });

        auditService.logEvent(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                "USER_LOGIN",
                "AUTH",
                user.getId().toString(),
                "User successfully logged in",
                auditService.extractClientIp(request),
                "SUCCESS"
        );

        // Redirect based on role
        Role role = user.getRole();
        String targetUrl;
        switch (role) {
            case CUSTOMER -> targetUrl = "/customer/dashboard";
            case TELLER -> targetUrl = "/staff/teller";
            case CUSTOMER_SERVICE_OFFICER -> targetUrl = "/staff/cso";
            case LOAN_OFFICER -> targetUrl = "/staff/loans";
            case BRANCH_MANAGER, REGIONAL_MANAGER -> targetUrl = "/manager/dashboard";
            case COMPLIANCE_OFFICER, RISK_OFFICER -> targetUrl = "/compliance/dashboard";
            case AUDITOR -> targetUrl = "/auditor/dashboard";
            case BANK_ADMIN, SUPER_ADMIN -> targetUrl = "/admin/dashboard";
            case EXECUTIVE -> targetUrl = "/executive/dashboard";
            default -> targetUrl = "/dashboard";
        }

        response.sendRedirect(targetUrl);
    }
}
