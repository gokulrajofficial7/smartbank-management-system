package com.smartbank.controller;

import com.smartbank.entity.Customer;
import com.smartbank.exception.BankingException;
import com.smartbank.service.CustomerService;
import com.smartbank.service.TransactionService;
import com.smartbank.entity.Role;
import com.smartbank.entity.User;
import com.smartbank.security.AuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final AuthorizationService authorizationService;

    // --- List All Customers ---
    @GetMapping
    public String listCustomers(Model model) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to access staff customer directory", currentUser.getUsername());
            throw new AccessDeniedException("Forbidden: Customer directory access is restricted to bank staff.");
        }
        List<Customer> customers = customerService.getAllCustomers();
        model.addAttribute("customers", customers);
        model.addAttribute("totalCount", customers.size());
        return "customer-list";
    }

    // --- Search Customers ---
    @GetMapping("/search")
    public String searchCustomers(@RequestParam(required = false) String query, Model model) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to search staff customer directory", currentUser.getUsername());
            throw new AccessDeniedException("Forbidden: Customer search is restricted to bank staff.");
        }
        if (query != null && !query.trim().isEmpty()) {
            model.addAttribute("customers", customerService.searchCustomers(query.trim()));
            model.addAttribute("searchQuery", query);
        } else {
            model.addAttribute("customers", customerService.getAllCustomers());
        }
        return "customer-list";
    }

    // --- Create Customer Form ---
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to access staff new customer creation form", currentUser.getUsername());
            throw new AccessDeniedException("Forbidden: New account creation is restricted to bank staff.");
        }
        model.addAttribute("customer", new Customer());
        return "create-customer";
    }

    // --- Create Customer Submit ---
    @PostMapping("/create")
    public String createCustomer(@Valid @ModelAttribute("customer") Customer customer,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to submit staff new customer creation", currentUser.getUsername());
            throw new AccessDeniedException("Forbidden: New account creation is restricted to bank staff.");
        }
        if (bindingResult.hasErrors()) {
            return "create-customer";
        }

        try {
            Customer created = customerService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Account created successfully! Account Number: " + created.getAccountNumber());
            return "redirect:/customers/" + created.getId();
        } catch (BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "create-customer";
        }
    }

    // --- View Customer Details ---
    @GetMapping("/{id}")
    public String viewCustomer(@PathVariable Long id, Model model) {
        Customer customer = customerService.getCustomerById(id);
        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactionService.getTransactionsByCustomer(customer));
        return "customer-details";
    }

    // --- Search by Account Number ---
    @GetMapping("/account/{accountNumber}")
    public String viewByAccountNumber(@PathVariable String accountNumber, Model model) {
        Customer customer = customerService.getCustomerByAccountNumber(accountNumber);
        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactionService.getTransactionsByCustomer(customer));
        return "customer-details";
    }

    // --- Edit Customer Form ---
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to access staff edit form for customer ID: {}", currentUser.getUsername(), id);
            throw new AccessDeniedException("Forbidden: Customer editing is restricted to bank staff.");
        }
        model.addAttribute("customer", customerService.getCustomerById(id));
        return "edit-customer";
    }

    // --- Edit Customer Submit ---
    @PostMapping("/{id}/edit")
    public String updateCustomer(@PathVariable Long id,
                                  @Valid @ModelAttribute("customer") Customer customer,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to edit customer ID: {}", currentUser.getUsername(), id);
            throw new AccessDeniedException("Forbidden: Customer editing is restricted to bank staff.");
        }
        if (bindingResult.hasErrors()) {
            return "edit-customer";
        }

        try {
            customerService.updateCustomer(id, customer);
            redirectAttributes.addFlashAttribute("successMessage", "Customer details updated successfully!");
            return "redirect:/customers/" + id;
        } catch (BankingException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "edit-customer";
        }
    }

    // --- Delete Customer ---
    @PostMapping("/{id}/delete")
    public String deleteCustomer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User currentUser = authorizationService.getRequiredCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            log.warn("[AUTH-ROUTE-DENIAL] Customer [{}] attempted to delete customer ID: {}", currentUser.getUsername(), id);
            throw new AccessDeniedException("Forbidden: Account deletion is restricted to bank staff.");
        }
        customerService.deleteCustomer(id);
        redirectAttributes.addFlashAttribute("successMessage", "Customer account deleted successfully.");
        return "redirect:/customers";
    }
}
