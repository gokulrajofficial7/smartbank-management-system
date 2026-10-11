package com.smartbank.security;

import com.smartbank.entity.User;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String errorMessage = "Invalid username or password";

        if (username != null && !username.trim().isEmpty()) {
            Optional<User> userOpt = userRepository.findByUsername(username.trim());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (Boolean.FALSE.equals(user.getAccountNonLocked()) || exception instanceof LockedException) {
                    errorMessage = "Account is locked due to multiple failed attempts. Please try again after 15 minutes or contact support.";
                } else {
                    int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
                    user.setFailedLoginAttempts(attempts);

                    if (attempts >= MAX_FAILED_ATTEMPTS) {
                        user.setAccountNonLocked(false);
                        user.setLockoutTime(LocalDateTime.now().plusMinutes(15));
                        errorMessage = "Account locked for 15 minutes due to 5 consecutive failed attempts.";
                        log.warn("Account locked for user: {}", username);
                    }
                    userRepository.save(user);
                }

                auditService.logEvent(
                        user.getId(),
                        username,
                        user.getRole().name(),
                        "FAILED_LOGIN",
                        "AUTH",
                        user.getId().toString(),
                        "Failed login attempt. Count: " + user.getFailedLoginAttempts(),
                        auditService.extractClientIp(request),
                        "FAILED"
                );
            } else {
                auditService.logEvent(
                        null,
                        username,
                        "UNKNOWN",
                        "FAILED_LOGIN",
                        "AUTH",
                        null,
                        "Failed login attempt for non-existent user",
                        auditService.extractClientIp(request),
                        "FAILED"
                );
            }
        }

        String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        response.sendRedirect("/login?error=true&message=" + encodedError);
    }
}
