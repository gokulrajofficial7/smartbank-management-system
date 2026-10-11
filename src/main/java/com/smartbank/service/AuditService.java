package com.smartbank.service;

import com.smartbank.entity.AuditLog;
import com.smartbank.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(Long userId, String username, String role, String action,
                         String resource, String resourceId, String details,
                         String ipAddress, String status) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username != null ? username : "ANONYMOUS")
                    .role(role != null ? role : "UNAUTHENTICATED")
                    .action(action)
                    .resource(resource)
                    .resourceId(resourceId)
                    .details(details)
                    .ipAddress(ipAddress != null ? ipAddress : "UNKNOWN")
                    .status(status != null ? status : "SUCCESS")
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("[AUDIT] Action: {}, Resource: {}, User: {}, Status: {}", action, resource, username, status);
        } catch (Exception e) {
            log.error("[AUDIT FAILURE] Failed to write audit log: {}", e.getMessage(), e);
        }
    }

    public List<AuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    public List<AuditLog> getSecurityViolations() {
        return auditLogRepository.findSecurityViolations();
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
