package com.smartbank;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.*;
import com.smartbank.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BeneficiaryManagementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User userAlpha;
    private Customer customerAlpha;
    private Account accountAlpha;

    private User userBeta;
    private Customer customerBeta;
    private Account accountBeta;

    private Branch branch;

    @BeforeEach
    void setUp() {
        branch = branchRepository.findAll().stream().findFirst().orElseGet(() -> {
            Branch b = Branch.builder().branchCode("BR-TEST").branchName("Test Branch").city("Chennai").address("TN").build();
            return branchRepository.save(b);
        });

        // Setup Customer Alpha
        userAlpha = userRepository.findByUsername("test_alpha").orElseGet(() -> {
            User u = User.builder()
                    .username("test_alpha")
                    .password(passwordEncoder.encode("Pass@123"))
                    .email("alpha@testbank.com")
                    .fullName("Customer Alpha")
                    .role(Role.CUSTOMER)
                    .branch(branch)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            return userRepository.save(u);
        });

        customerAlpha = customerRepository.findByUserId(userAlpha.getId()).orElseGet(() -> {
            Customer c = Customer.builder()
                    .name("Customer Alpha")
                    .email("alpha@testbank.com")
                    .phone("9888811111")
                    .accountNumber("SB_ALPHA_001")
                    .balance(new BigDecimal("50000.00"))
                    .user(userAlpha)
                    .branch(branch)
                    .status(CustomerStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .build();
            return customerRepository.save(c);
        });

        accountAlpha = accountRepository.findByAccountNumber("SB_ALPHA_001").orElseGet(() -> {
            Account acc = Account.builder()
                    .accountNumber("SB_ALPHA_001")
                    .accountType(AccountType.SAVINGS)
                    .customer(customerAlpha)
                    .branch(branch)
                    .balance(new BigDecimal("50000.00"))
                    .status(AccountStatus.ACTIVE)
                    .build();
            return accountRepository.save(acc);
        });

        // Setup Customer Beta
        userBeta = userRepository.findByUsername("test_beta").orElseGet(() -> {
            User u = User.builder()
                    .username("test_beta")
                    .password(passwordEncoder.encode("Pass@123"))
                    .email("beta@testbank.com")
                    .fullName("Customer Beta")
                    .role(Role.CUSTOMER)
                    .branch(branch)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            return userRepository.save(u);
        });

        customerBeta = customerRepository.findByUserId(userBeta.getId()).orElseGet(() -> {
            Customer c = Customer.builder()
                    .name("Customer Beta")
                    .email("beta@testbank.com")
                    .phone("9888822222")
                    .accountNumber("SB_BETA_002")
                    .balance(new BigDecimal("75000.00"))
                    .user(userBeta)
                    .branch(branch)
                    .status(CustomerStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .build();
            return customerRepository.save(c);
        });

        accountBeta = accountRepository.findByAccountNumber("SB_BETA_002").orElseGet(() -> {
            Account acc = Account.builder()
                    .accountNumber("SB_BETA_002")
                    .accountType(AccountType.SAVINGS)
                    .customer(customerBeta)
                    .branch(branch)
                    .balance(new BigDecimal("75000.00"))
                    .status(AccountStatus.ACTIVE)
                    .build();
            return accountRepository.save(acc);
        });
    }

    @Test
    @DisplayName("1. Unauthenticated request to /customer/beneficiaries redirects to /login")
    void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/customer/beneficiaries"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("2. Authenticated customer with NO beneficiaries sees friendly empty state")
    void testCustomerWithNoBeneficiariesRendersEmptyState() throws Exception {
        mockMvc.perform(get("/customer/beneficiaries"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/beneficiaries"))
                .andExpect(model().attributeExists("customer", "beneficiaries"))
                .andExpect(content().string(containsString("No Beneficiaries Saved Yet")))
                .andExpect(content().string(containsString("Add Your First Beneficiary")));
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("3. Authenticated customer with beneficiaries renders populated table and cooling-off badge")
    void testCustomerWithBeneficiariesRendersTableAndCoolingOff() throws Exception {
        // Add Beta as beneficiary of Alpha
        Beneficiary ben = accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta Account");
        assertNotNull(ben.getId());
        assertTrue(ben.isCoolingOffActive());

        mockMvc.perform(get("/customer/beneficiaries"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/beneficiaries"))
                .andExpect(content().string(containsString("Customer Beta")))
                .andExpect(content().string(containsString("SB_BETA_002")))
                .andExpect(content().string(containsString("Cooling-Off Active")))
                .andExpect(content().string(containsString("Beta Account")))
                .andExpect(content().string(not(containsString("No Beneficiaries Saved Yet"))));
    }

    @Test
    @DisplayName("4. Multiple customers have strictly isolated beneficiary records")
    void testCustomerDataIsolation() {
        // As Alpha, add Beta as beneficiary
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_alpha", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        Beneficiary alphaBen = accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta");
        List<Beneficiary> alphaList = accountService.getMyBeneficiaries();
        assertEquals(1, alphaList.size());
        assertEquals("SB_BETA_002", alphaList.get(0).getTargetAccountNumber());

        // Switch to Beta: Beta should see 0 beneficiaries initially
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_beta", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        List<Beneficiary> betaList = accountService.getMyBeneficiaries();
        assertTrue(betaList.isEmpty(), "Beta must not see Alpha's saved beneficiaries");

        // As Beta, add external beneficiary
        accountService.addBeneficiary("987654321098", "Ramesh Kumar", "HDFC Bank", "Friend");
        betaList = accountService.getMyBeneficiaries();
        assertEquals(1, betaList.size());
        assertEquals("987654321098", betaList.get(0).getTargetAccountNumber());

        // Switch back to Alpha: Alpha should still only see Beta
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_alpha", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        alphaList = accountService.getMyBeneficiaries();
        assertEquals(1, alphaList.size());
        assertEquals("SB_BETA_002", alphaList.get(0).getTargetAccountNumber());
    }

    @Test
    @DisplayName("5. Customer cannot access or delete another customer's beneficiary (IDOR protection)")
    void testCrossCustomerBeneficiaryDeleteBlocked() {
        // Alpha creates beneficiary
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_alpha", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        Beneficiary alphaBen = accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta");
        Long alphaBenId = alphaBen.getId();

        // Beta attempts to delete Alpha's beneficiary
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_beta", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        BankingException ex = assertThrows(BankingException.class, () -> accountService.deleteBeneficiary(alphaBenId));
        assertTrue(ex.getMessage().contains("not found or access denied"));

        // Verify Alpha's beneficiary remains intact and active
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_alpha", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        List<Beneficiary> alphaList = accountService.getMyBeneficiaries();
        assertEquals(1, alphaList.size());
        assertTrue(alphaList.get(0).getActive());
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("6. Input validation: empty account number is rejected")
    void testEmptyAccountNumberRejected() {
        BankingException ex = assertThrows(BankingException.class, () ->
                accountService.addBeneficiary("", "John Doe", "SmartBank", "Nick"));
        assertEquals("Beneficiary account number is required.", ex.getMessage());
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("7. Input validation: empty account name is rejected")
    void testEmptyAccountNameRejected() {
        BankingException ex = assertThrows(BankingException.class, () ->
                accountService.addBeneficiary("SB_BETA_002", "   ", "SmartBank", "Nick"));
        assertEquals("Beneficiary account holder name is required.", ex.getMessage());
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("8. Input validation: adding own account as beneficiary is strictly rejected")
    void testAddingOwnAccountRejected() {
        BankingException ex = assertThrows(BankingException.class, () ->
                accountService.addBeneficiary("SB_ALPHA_001", "Customer Alpha", "SmartBank", "My Self"));
        assertEquals("Cannot add your own account as a beneficiary.", ex.getMessage());
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("9. Input validation: adding non-existent SmartBank account is rejected")
    void testNonExistentSmartBankAccountRejected() {
        BankingException ex = assertThrows(BankingException.class, () ->
                accountService.addBeneficiary("SB_NON_EXISTENT_999", "Ghost Account", "SmartBank", "Ghost"));
        assertTrue(ex.getMessage().contains("was not found. Please verify the account number"));
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("10. Input validation: adding closed SmartBank account is rejected")
    void testClosedSmartBankAccountRejected() {
        accountBeta.setStatus(AccountStatus.CLOSED);
        accountRepository.save(accountBeta);

        BankingException ex = assertThrows(BankingException.class, () ->
                accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Closed Beta"));
        assertTrue(ex.getMessage().contains("is closed and cannot receive funds"));
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("11. Duplicate active beneficiary is rejected with clear message")
    void testDuplicateActiveBeneficiaryRejected() {
        accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta 1");

        BankingException ex = assertThrows(BankingException.class, () ->
                accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta 2"));
        assertTrue(ex.getMessage().contains("is already in your active beneficiary list"));
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("12. Cooling-off rules and deletion lifecycle: newly added beneficiary has 4-hour cooling-off; deletion deactivates cleanly")
    void testCoolingOffAndDeletionLifecycle() {
        Beneficiary saved = accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta");
        assertNotNull(saved.getCoolingOffUntil());
        assertTrue(saved.getCoolingOffUntil().isAfter(LocalDateTime.now().plusHours(3).plusMinutes(50)));
        assertTrue(saved.isCoolingOffActive());

        // Delete beneficiary
        accountService.deleteBeneficiary(saved.getId());

        // Verify it is no longer returned in active list
        List<Beneficiary> currentList = accountService.getMyBeneficiaries();
        assertTrue(currentList.isEmpty());

        // In DB, active is false (soft delete for audit trail)
        Beneficiary inDb = beneficiaryRepository.findById(saved.getId()).orElseThrow();
        assertFalse(inDb.getActive());

        // Can re-add previously deleted beneficiary: it reactivates and resets cooling-off
        Beneficiary reactivated = accountService.addBeneficiary("SB_BETA_002", "Customer Beta Updated", "SmartBank", "Re-added");
        assertEquals(saved.getId(), reactivated.getId());
        assertTrue(reactivated.getActive());
        assertEquals("Customer Beta Updated", reactivated.getTargetAccountName());
        assertTrue(reactivated.isCoolingOffActive());
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("13. Web MVC: POST /customer/beneficiaries adds beneficiary and redirects with flash message")
    void testPostAddBeneficiaryFlow() throws Exception {
        mockMvc.perform(post("/customer/beneficiaries")
                        .with(csrf())
                        .param("targetAccountNumber", "SB_BETA_002")
                        .param("targetAccountName", "Customer Beta")
                        .param("bankName", "SmartBank")
                        .param("nickname", "Beta Rent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/beneficiaries"))
                .andExpect(flash().attributeExists("successMessage"));

        List<Beneficiary> bens = beneficiaryRepository.findByCustomerIdAndActiveTrue(customerAlpha.getId());
        assertEquals(1, bens.size());
        assertEquals("SB_BETA_002", bens.get(0).getTargetAccountNumber());
    }

    @Test
    @WithMockUser(username = "test_alpha", roles = {"CUSTOMER"})
    @DisplayName("14. Web MVC: POST /customer/beneficiaries/{id}/delete removes beneficiary and redirects with flash message")
    void testPostDeleteBeneficiaryFlow() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_alpha", "Pass@123",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        Beneficiary saved = accountService.addBeneficiary("SB_BETA_002", "Customer Beta", "SmartBank", "Beta");

        mockMvc.perform(post("/customer/beneficiaries/" + saved.getId() + "/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/beneficiaries"))
                .andExpect(flash().attributeExists("successMessage"));

        List<Beneficiary> activeBens = beneficiaryRepository.findByCustomerIdAndActiveTrue(customerAlpha.getId());
        assertTrue(activeBens.isEmpty());
    }
}
