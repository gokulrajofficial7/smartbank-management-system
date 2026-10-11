package com.smartbank;

import com.smartbank.entity.Account;
import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DepositAndAccountAccessSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // =========================================================================
    // 1. OWNER ACCESSING THEIR OWN ACCOUNT
    // =========================================================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Owner access: Customer A can access their own account details by account number")
    void testOwnerCanAccessOwnAccountDetails() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));

        mockMvc.perform(get("/customers/account/" + customerA.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-details"))
                .andExpect(model().attributeExists("customer"))
                .andExpect(content().string(containsString(customerA.getName())));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Owner deposit: Customer A can successfully deposit into their own account")
    void testOwnerCanDepositIntoOwnAccount() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));
        BigDecimal initialBalance = customerA.getBalance();
        BigDecimal depositAmount = new BigDecimal("500.00");

        mockMvc.perform(post("/deposit")
                        .with(csrf())
                        .param("accountNumber", customerA.getAccountNumber())
                        .param("amount", depositAmount.toString())
                        .param("description", "Owner self-deposit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers/account/" + customerA.getAccountNumber()))
                .andExpect(flash().attributeExists("successMessage"));

        Customer updatedCustomer = customerRepository.findById(customerA.getId()).orElseThrow();
        assertEquals(0, initialBalance.add(depositAmount).compareTo(updatedCustomer.getBalance()),
                "Customer balance should increase by deposit amount");

        Account updatedAccount = accountRepository.findByAccountNumber(customerA.getAccountNumber()).orElseThrow();
        assertEquals(0, updatedCustomer.getBalance().compareTo(updatedAccount.getBalance()),
                "Account balance should stay synchronized with customer balance");
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Deposit form: Deposit page automatically pre-populates the customer's own account number")
    void testDepositFormPrePopulatesOwnAccountForCustomer() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));

        mockMvc.perform(get("/deposit"))
                .andExpect(status().isOk())
                .andExpect(view().name("deposit"))
                .andExpect(model().attribute("accountNumber", customerA.getAccountNumber()))
                .andExpect(model().attributeExists("customer"));
    }

    // =========================================================================
    // 2. ANOTHER CUSTOMER ATTEMPTING ACCESS (IDOR / BOLA PREVENTION)
    // =========================================================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("IDOR Prevention: Customer A cannot view Customer B's account details")
    void testCustomerCannotAccessOtherCustomerAccountDetails() throws Exception {
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        mockMvc.perform(get("/customers/account/" + customerB.getAccountNumber()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("BOLA Prevention: Customer A cannot deposit into Customer B's account")
    void testCustomerCannotDepositIntoOtherCustomerAccount() throws Exception {
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));
        BigDecimal initialBalance = customerB.getBalance();

        mockMvc.perform(post("/deposit")
                        .with(csrf())
                        .param("accountNumber", customerB.getAccountNumber())
                        .param("amount", "250.00")
                        .param("description", "Unauthorized deposit into another customer account"))
                .andExpect(status().isForbidden());

        Customer refreshedB = customerRepository.findById(customerB.getId()).orElseThrow();
        assertEquals(0, initialBalance.compareTo(refreshedB.getBalance()),
                "Target customer balance must remain untouched after unauthorized deposit attempt");
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Deposit form query tampering: Entering another customer's account displays Access Denied warning")
    void testCustomerEnteringOtherAccountOnDepositPageShowsAccessDeniedWarning() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));
        Customer customerB = customerRepository.findByUserUsername("customer_b")
                .orElseThrow(() -> new IllegalStateException("customer_b must exist"));

        mockMvc.perform(get("/deposit")
                        .param("accountNumber", customerB.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(view().name("deposit"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("accountNumber", customerA.getAccountNumber()))
                .andExpect(content().string(containsString("Access Denied")));
    }

    // =========================================================================
    // 3. AUTHORIZED STAFF ACCESSING ELIGIBLE ACCOUNT VS CROSS-BRANCH DENIAL
    // =========================================================================

    @Test
    @WithMockUser(username = "teller_a", roles = {"TELLER"})
    @DisplayName("Branch Staff: Teller A can view account details of customer in the same branch")
    void testTellerCanAccessEligibleAccountInOwnBranch() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));

        mockMvc.perform(get("/customers/account/" + customerA.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-details"))
                .andExpect(model().attributeExists("customer"));
    }

    @Test
    @WithMockUser(username = "teller_a", roles = {"TELLER"})
    @DisplayName("Branch Staff: Teller A can deposit into account of customer in the same branch")
    void testTellerCanDepositIntoEligibleAccountInOwnBranch() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));
        BigDecimal initialBalance = customerA.getBalance();
        BigDecimal depositAmount = new BigDecimal("1000.00");

        mockMvc.perform(post("/deposit")
                        .with(csrf())
                        .param("accountNumber", customerA.getAccountNumber())
                        .param("amount", depositAmount.toString())
                        .param("description", "Teller cash deposit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers/account/" + customerA.getAccountNumber()));

        Customer refreshed = customerRepository.findById(customerA.getId()).orElseThrow();
        assertEquals(0, initialBalance.add(depositAmount).compareTo(refreshed.getBalance()),
                "Balance must increase after authorized teller deposit");
    }

    @Test
    @WithMockUser(username = "teller_b", roles = {"TELLER"})
    @DisplayName("Branch Isolation: Teller B (Branch 2) cannot view account details of Branch 1 customer")
    void testTellerCannotAccessAccountInAnotherBranch() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));

        mockMvc.perform(get("/customers/account/" + customerA.getAccountNumber()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "teller_b", roles = {"TELLER"})
    @DisplayName("Branch Isolation: Teller B (Branch 2) cannot deposit into Branch 1 customer account")
    void testTellerCannotDepositIntoAccountInAnotherBranch() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));
        BigDecimal initialBalance = customerA.getBalance();

        mockMvc.perform(post("/deposit")
                        .with(csrf())
                        .param("accountNumber", customerA.getAccountNumber())
                        .param("amount", "500.00")
                        .param("description", "Cross branch unauthorized deposit"))
                .andExpect(status().isForbidden());

        Customer refreshed = customerRepository.findById(customerA.getId()).orElseThrow();
        assertEquals(0, initialBalance.compareTo(refreshed.getBalance()),
                "Balance must remain unchanged after cross-branch deposit attempt");
    }

    // =========================================================================
    // 4. CUSTOMER ATTEMPTING PROHIBITED STAFF-ONLY OPERATIONS
    // =========================================================================

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Staff-Only Restriction: Customer cannot access staff customer directory (/customers)")
    void testCustomerCannotAccessStaffCustomerDirectory() throws Exception {
        mockMvc.perform(get("/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Staff-Only Restriction: Customer cannot perform staff customer search (/customers/search)")
    void testCustomerCannotAccessStaffCustomerSearch() throws Exception {
        mockMvc.perform(get("/customers/search")
                        .param("query", "Appu"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Staff-Only Restriction: Customer cannot access staff new account form (/customers/create)")
    void testCustomerCannotAccessStaffCustomerCreateForm() throws Exception {
        mockMvc.perform(get("/customers/create"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Staff-Only Restriction: Customer cannot submit staff customer creation (POST /customers/create)")
    void testCustomerCannotSubmitStaffCustomerCreate() throws Exception {
        mockMvc.perform(post("/customers/create")
                        .with(csrf())
                        .param("name", "Malicious Customer")
                        .param("email", "malicious@test.com")
                        .param("phone", "9876543210")
                        .param("balance", "100000.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Staff-Only Restriction: Customer cannot access staff teller portal (/staff/teller)")
    void testCustomerCannotAccessStaffTellerDesk() throws Exception {
        mockMvc.perform(get("/staff/teller"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("Staff-Only Restriction: Customer cannot delete a customer account (POST /customers/{id}/delete)")
    void testCustomerCannotDeleteCustomerDirectly() throws Exception {
        Customer customerA = customerRepository.findByUserUsername("customer_a")
                .orElseThrow(() -> new IllegalStateException("customer_a must exist"));

        mockMvc.perform(post("/customers/" + customerA.getId() + "/delete")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
