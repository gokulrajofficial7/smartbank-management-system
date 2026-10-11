package com.smartbank.security;

import com.smartbank.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditService auditService;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "ANONYMOUS";
        String role = (auth != null && !auth.getAuthorities().isEmpty()) ? auth.getAuthorities().iterator().next().getAuthority() : "NONE";

        log.warn("[AUTH-ROUTE-DENIAL] Route-level access denied: User [{}] (Role [{}]) blocked from URI [{}] - Reason: {}",
                username, role, request.getRequestURI(), accessDeniedException.getMessage());

        auditService.logEvent(
                null,
                username,
                role,
                "ACCESS_DENIED",
                request.getRequestURI(),
                null,
                "Unauthorized attempt to access protected resource: " + accessDeniedException.getMessage(),
                auditService.extractClientIp(request),
                "DENIED"
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("errorMessage", "Access Denied: You do not have permission to view or modify this resource.");
        request.getRequestDispatcher("/access-denied").forward(request, response);
    }
}
