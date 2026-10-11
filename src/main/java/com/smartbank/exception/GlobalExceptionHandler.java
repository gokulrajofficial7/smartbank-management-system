package com.smartbank.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model, HttpServletResponse response) {
        log.warn("[AUTH-SERVICE-DENIAL] Service-layer AccessDeniedException handled by ControllerAdvice: {}", ex.getMessage());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("errorTitle", "Access Denied (403)");
        model.addAttribute("errorMessage", ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Access Denied: You do not have permission to access or modify this banking resource.");
        return "access-denied";
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public String handleInsufficientFunds(InsufficientFundsException ex, Model model) {
        log.warn("[TRANSACTION-REJECT] Insufficient funds: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Insufficient Funds");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public String handleCustomerNotFound(CustomerNotFoundException ex, Model model) {
        log.warn("[RESOURCE-NOT-FOUND] Customer lookup failure: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Customer Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public String handleAccountNotFound(AccountNotFoundException ex, Model model) {
        log.warn("[RESOURCE-NOT-FOUND] Account lookup failure: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Account Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(BankingException.class)
    public String handleBankingException(BankingException ex, Model model) {
        log.warn("[BANKING-VALIDATION] Operation rejected: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Banking Operation Error");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public String handleConcurrencyConflict(Exception ex, HttpServletRequest request, Model model) {
        String correlationId = generateReferenceId();
        log.warn("[CONCURRENCY-CONFLICT] Ref: [{}], URI: [{}], Reason: {}",
                correlationId, request != null ? request.getRequestURI() : "N/A", ex.getMessage());
        model.addAttribute("errorTitle", "Temporary Concurrency Conflict");
        model.addAttribute("errorMessage", "Your transaction could not be completed because another operation was modifying this account simultaneously. No funds were debited. Please retry your transaction.");
        model.addAttribute("referenceId", correlationId);
        return "error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request, Model model) {
        String correlationId = generateReferenceId();
        log.warn("[DATA-INTEGRITY] Ref: [{}], URI: [{}], Constraint violation: {}",
                correlationId, request != null ? request.getRequestURI() : "N/A", ex.getMessage());
        model.addAttribute("errorTitle", "Data Validation Error");
        model.addAttribute("errorMessage", "A database data constraint prevented this transaction from completing. No financial balance changes occurred.");
        model.addAttribute("referenceId", correlationId);
        return "error";
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class
    })
    public String handleValidationErrors(Exception ex, Model model) {
        log.warn("[INPUT-VALIDATION] Request binding/validation error: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Invalid Request Parameters");
        model.addAttribute("errorMessage", "Please ensure all transaction fields and amounts are properly formatted and valid.");
        return "error";
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public String handleIllegalStateOrArgument(RuntimeException ex, Model model) {
        log.warn("[BUSINESS-RULE] Illegal argument or state: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Banking Operation Error");
        model.addAttribute("errorMessage", ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "The requested banking operation could not be completed due to invalid parameters.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, HttpServletRequest request, Model model) {
        String referenceId = generateReferenceId();
        log.error("[SYSTEM-ERROR] Ref: [{}], URI: [{}], Exception: {}",
                referenceId, request != null ? request.getRequestURI() : "N/A", ex.getMessage(), ex);
        model.addAttribute("errorTitle", "System Error");
        model.addAttribute("errorMessage", "An unexpected error occurred while processing your banking request. Please try again later.");
        model.addAttribute("referenceId", referenceId);
        return "error";
    }

    private String generateReferenceId() {
        return "ERR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
