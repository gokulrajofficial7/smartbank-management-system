package com.smartbank.security;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails.getUser());
        }
        return userRepository.findByUsername(auth.getName());
    }

    public User getRequiredCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
                new AccessDeniedException("User must be authenticated to perform this operation"));
    }

    public Optional<Customer> getCurrentCustomer() {
        User user = getCurrentUser().orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        return customerRepository.findByUserId(user.getId())
                .or(() -> customerRepository.findByUserUsername(user.getUsername()));
    }

    /**
     * Strict Customer Isolation Check:
     * A Customer role user can ONLY access their own customer profile.
     */
    public void verifyCustomerAccess(Long customerId) {
        User currentUser = getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = getCurrentCustomer().orElseThrow(() ->
                    new AccessDeniedException("No customer profile linked to authenticated account"));
            if (!customer.getId().equals(customerId)) {
                log.warn("[AUTH-OWNERSHIP-DENIAL] Customer {} (Customer ID: {}) attempted to access Customer ID: {}",
                        currentUser.getUsername(), customer.getId(), customerId);
                throw new AccessDeniedException("Forbidden: You do not have permission to view or manage this customer.");
            }
        }
        // Branch staff check: staff can only see customers of their own branch if branch is assigned
        if (currentUser.getRole() == Role.TELLER || currentUser.getRole() == Role.CUSTOMER_SERVICE_OFFICER || currentUser.getRole() == Role.BRANCH_MANAGER) {
            if (currentUser.getBranch() != null) {
                Customer target = customerRepository.findById(customerId).orElse(null);
                if (target != null && target.getBranch() != null &&
                        !target.getBranch().getId().equals(currentUser.getBranch().getId())) {
                    log.warn("[AUTH-BRANCH-DENIAL] Staff user {} (Role: {}, Branch: {}) attempted to access Customer {} outside branch",
                            currentUser.getUsername(), currentUser.getRole(), currentUser.getBranch().getBranchCode(), customerId);
                    throw new AccessDeniedException("Forbidden: Customer belongs to another branch.");
                }
            }
        }
    }

    /**
     * Strict Branch Scope Enforcement for Branch Managers:
     * Derives authorization scope strictly from the authenticated security context and database.
     * Never trusts client-supplied branch parameters.
     */
    public void verifyManagerBranchAccess(User manager, Customer customer) {
        if (manager == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        if (manager.getRole() == Role.BRANCH_MANAGER) {
            if (manager.getBranch() == null) {
                log.warn("[AUTH-BRANCH-DENIAL] Manager {} has no branch assignment configured in database", manager.getUsername());
                throw new AccessDeniedException("Forbidden: No branch assigned to manager.");
            }
            if (customer == null || customer.getBranch() == null ||
                    !customer.getBranch().getId().equals(manager.getBranch().getId())) {
                log.warn("[AUTH-BRANCH-DENIAL] Branch Manager {} (Branch: {}) attempted unauthorized access to customer {} (Branch: {})",
                        manager.getUsername(),
                        manager.getBranch().getBranchCode(),
                        customer != null ? customer.getId() : "null",
                        customer != null && customer.getBranch() != null ? customer.getBranch().getBranchCode() : "NONE");
                throw new AccessDeniedException("Forbidden: Customer does not belong to your assigned branch.");
            }
        }
    }

    /**
     * Strict Account Ownership Check:
     * A Customer role user can ONLY view or operate on accounts that belong to them.
     */
    public void verifyAccountAccess(String accountNumber) {
        User currentUser = getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = getCurrentCustomer().orElseThrow(() ->
                    new AccessDeniedException("No customer profile linked to authenticated account"));
            // Check legacy customer account number match
            boolean matchesCustomerAccount = customer.getAccountNumber().equalsIgnoreCase(accountNumber);
            // Check account repository match without loading entity into session
            boolean matchesOwnedAccount = matchesCustomerAccount || accountRepository.existsByAccountNumberAndCustomerId(accountNumber, customer.getId());

            if (!matchesCustomerAccount && !matchesOwnedAccount) {
                log.warn("[AUTH-OWNERSHIP-DENIAL] Customer {} attempted to access Account: {}",
                        currentUser.getUsername(), accountNumber);
                throw new AccessDeniedException("Forbidden: You do not have ownership of account " + accountNumber);
            }
        }
        // Cross-branch staff check for accounts
        if (currentUser.getRole() == Role.TELLER || currentUser.getRole() == Role.CUSTOMER_SERVICE_OFFICER || currentUser.getRole() == Role.BRANCH_MANAGER) {
            if (currentUser.getBranch() != null) {
                Long targetBranchId = accountRepository.findByAccountNumber(accountNumber)
                        .map(acc -> acc.getBranch() != null ? acc.getBranch().getId() : null)
                        .orElseGet(() -> customerRepository.findByAccountNumber(accountNumber)
                                .map(c -> c.getBranch() != null ? c.getBranch().getId() : null)
                                .orElse(null));
                if (targetBranchId != null && !targetBranchId.equals(currentUser.getBranch().getId())) {
                    log.warn("[AUTH-BRANCH-DENIAL] Staff user {} (Role: {}, Branch: {}) attempted to access Account {} outside branch",
                            currentUser.getUsername(), currentUser.getRole(), currentUser.getBranch().getBranchCode(), accountNumber);
                    throw new AccessDeniedException("Forbidden: Account belongs to another branch.");
                }
            }
        }
    }

    /**
     * Strict Account Ownership Check by Account ID
     */
    public void verifyAccountAccess(Long accountId) {
        User currentUser = getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = getCurrentCustomer().orElseThrow(() ->
                    new AccessDeniedException("No customer profile linked to authenticated account"));
            Account account = accountRepository.findById(accountId).orElseThrow(() ->
                    new BankingException("Account not found with ID: " + accountId));
            if (!account.getCustomer().getId().equals(customer.getId())) {
                log.warn("[SECURITY VIOLATION - IDOR PREVENTED] Customer {} attempted to access Account ID: {}",
                        currentUser.getUsername(), accountId);
                throw new AccessDeniedException("Forbidden: You do not own this account.");
            }
        }
    }

    /**
     * Maker-Checker Rule: Maker and Checker MUST be distinct users!
     */
    public void verifyMakerChecker(User maker, User checker) {
        if (maker != null && checker != null && maker.getId().equals(checker.getId())) {
            log.error("[MAKER-CHECKER VIOLATION] User {} attempted to approve their own request!", maker.getUsername());
            throw new BankingException("Dual-control violation: Maker cannot act as Checker for their own operation.");
        }
    }

    public boolean isCurrentCustomerOwner(String accountNumber) {
        try {
            verifyAccountAccess(accountNumber);
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }
}
