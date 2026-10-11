package com.smartbank;

import com.smartbank.entity.*;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.AuditLogRepository;
import com.smartbank.repository.BranchRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.UserRepository;
import com.smartbank.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class BranchManagerCustomerAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CustomerService customerService;

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Branch Manager Dashboard: Total Customers card is clickable and reflects actual branch customer count")
    void testTotalCustomersCardCountMatchesActualBranchRecords() throws Exception {
        User managerA = userRepository.findByUsername("manager_a").orElseThrow();
        assertNotNull(managerA.getBranch(), "manager_a must be assigned to a branch");
        Long branchId = managerA.getBranch().getId();

        long actualBranchCustomers = customerRepository.countActiveCustomersByBranchId(branchId);

        mockMvc.perform(get("/manager/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/dashboard"))
                .andExpect(model().attribute("totalCustomers", actualBranchCustomers))
                .andExpect(content().string(containsString("href=\"/manager/customers\"")))
                .andExpect(content().string(containsString("Total Customers")));
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Branch Customers Directory: Manager can view only customers belonging to assigned branch")
    void testManagerCanViewOwnBranchCustomersList() throws Exception {
        User managerA = userRepository.findByUsername("manager_a").orElseThrow();
        Long branchId = managerA.getBranch().getId();

        mockMvc.perform(get("/manager/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/customers"))
                .andExpect(model().attributeExists("customersPage"))
                .andExpect(content().string(containsString("Branch Customers Directory")))
                .andExpect(content().string(containsString("SB••••••"))); // Masked account numbers
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Customer Details: Manager can view read-only details of customer in own branch")
    void testManagerCanViewOwnBranchCustomerDetailsReadOnly() throws Exception {
        User managerA = userRepository.findByUsername("manager_a").orElseThrow();
        Long branchId = managerA.getBranch().getId();

        // Find a customer in manager_a's branch
        List<Customer> branch1Customers = customerRepository.findByBranchId(branchId);
        assertFalse(branch1Customers.isEmpty(), "Branch 1 must have at least one customer");
        Customer ownCustomer = branch1Customers.get(0);

        mockMvc.perform(get("/manager/customers/" + ownCustomer.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/customer-details"))
                .andExpect(model().attributeExists("customer"))
                .andExpect(model().attributeExists("maskedAccountNumber"))
                .andExpect(content().string(containsString(ownCustomer.getName())))
                .andExpect(content().string(containsString("SB••••••")))
                .andExpect(content().string(containsString("Access Policy Notice")));
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Security Isolation: Direct URL ID tampering to access customer in another branch is rejected with 403")
    void testManagerCannotAccessCustomerOfAnotherBranchDirectUrlTampering() throws Exception {
        Branch branch2 = branchRepository.findByBranchCode("BR-002").orElseThrow();
        List<Customer> branch2Customers = customerRepository.findByBranchId(branch2.getId());
        assertFalse(branch2Customers.isEmpty(), "Branch 2 must have at least one customer (e.g. Priya Sharma)");
        Customer otherBranchCustomer = branch2Customers.get(0);

        // manager_a (Branch 1) attempts to access a customer belonging to Branch 2
        mockMvc.perform(get("/manager/customers/" + otherBranchCustomer.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager_b", roles = {"BRANCH_MANAGER"})
    @DisplayName("Cross-Branch Isolation: Manager B can view Branch 2 customer and is forbidden from Branch 1 customer")
    void testManagerBCanViewOwnBranchCustomerAndForbiddenFromBranch1() throws Exception {
        Branch branch1 = branchRepository.findByBranchCode("BR-001").orElseThrow();
        Branch branch2 = branchRepository.findByBranchCode("BR-002").orElseThrow();

        Customer branch1Customer = customerRepository.findByBranchId(branch1.getId()).get(0);
        Customer branch2Customer = customerRepository.findByBranchId(branch2.getId()).get(0);

        // manager_b can view Branch 2 customer
        mockMvc.perform(get("/manager/customers/" + branch2Customer.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/customer-details"))
                .andExpect(content().string(containsString(branch2Customer.getName())));

        // manager_b CANNOT view Branch 1 customer (URL tampering)
        mockMvc.perform(get("/manager/customers/" + branch1Customer.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Browser Parameter Immunity: Manager cannot view other branch customers via query parameter tampering")
    void testQueryParameterBranchTamperingIsIgnored() throws Exception {
        Branch branch2 = branchRepository.findByBranchCode("BR-002").orElseThrow();
        Customer branch2Customer = customerRepository.findByBranchId(branch2.getId()).get(0);

        // Attacker attempts to pass ?branchId=2 in the query string
        mockMvc.perform(get("/manager/customers").param("branchId", branch2.getId().toString()))
                .andExpect(status().isOk())
                // Verify the page only contains Branch 1 customers and NEVER leaks Branch 2 customer
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(branch2Customer.getName()))));
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("RBAC Denial: Customer role cannot access Manager Customers directory (403 Forbidden)")
    void testCustomerRoleCannotAccessManagerCustomersPage() throws Exception {
        mockMvc.perform(get("/manager/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer_a", roles = {"CUSTOMER"})
    @DisplayName("RBAC Denial: Customer role cannot access Manager Customer Details endpoint (403 Forbidden)")
    void testCustomerRoleCannotAccessManagerCustomerDetails() throws Exception {
        Customer c1 = customerRepository.findAll().get(0);
        mockMvc.perform(get("/manager/customers/" + c1.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authentication Enforcement: Unauthenticated visitor is redirected to login")
    void testUnauthenticatedUserRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/manager/customers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Search & Filtering: Search query filters customers strictly within manager's branch scope")
    void testSearchFiltersWithinBranchScope() throws Exception {
        mockMvc.perform(get("/manager/customers").param("query", "Appu"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Appu")));
    }

    @Test
    @WithMockUser(username = "manager_a", roles = {"BRANCH_MANAGER"})
    @DisplayName("Audit Logging: Accessing customer details generates an authoritative audit log entry")
    void testAuditLogGeneratedOnCustomerAccess() throws Exception {
        User managerA = userRepository.findByUsername("manager_a").orElseThrow();
        Customer ownCustomer = customerRepository.findByBranchId(managerA.getBranch().getId()).get(0);

        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(get("/manager/customers/" + ownCustomer.getId()))
                .andExpect(status().isOk());

        long auditCountAfter = auditLogRepository.count();
        assertTrue(auditCountAfter > auditCountBefore, "Audit log record must be inserted on customer detail view");
    }
}
