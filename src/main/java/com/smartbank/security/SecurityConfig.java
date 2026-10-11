package com.smartbank.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler successHandler;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Static assets & public pages
                .requestMatchers(
                        "/",
                        "/index",
                        "/login",
                        "/register",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/favicon.svg",
                        "/error",
                        "/access-denied"
                ).permitAll()

                // Portal routes
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                .requestMatchers("/staff/**").hasAnyRole(
                        "TELLER", "CUSTOMER_SERVICE_OFFICER", "LOAN_OFFICER",
                        "OPERATIONS_OFFICER", "BRANCH_MANAGER", "REGIONAL_MANAGER",
                        "BANK_ADMIN", "SUPER_ADMIN"
                )
                .requestMatchers("/manager/**").hasAnyRole(
                        "BRANCH_MANAGER", "REGIONAL_MANAGER", "BANK_ADMIN", "SUPER_ADMIN"
                )
                .requestMatchers("/compliance/**").hasAnyRole(
                        "COMPLIANCE_OFFICER", "RISK_OFFICER", "AUDITOR", "BANK_ADMIN", "SUPER_ADMIN"
                )
                .requestMatchers("/auditor/**").hasAnyRole(
                        "AUDITOR", "SUPER_ADMIN"
                )
                .requestMatchers("/admin/**").hasAnyRole(
                        "BANK_ADMIN", "SUPER_ADMIN"
                )
                .requestMatchers("/executive/**").hasAnyRole(
                        "EXECUTIVE", "SUPER_ADMIN"
                )

                // Customer management and full customer directory - restricted to staff roles
                .requestMatchers("/customers/create", "/customers/*/edit", "/customers/*/delete").hasAnyRole(
                        "CUSTOMER_SERVICE_OFFICER", "BRANCH_MANAGER", "BANK_ADMIN", "SUPER_ADMIN"
                )
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/customers", "/customers/search").hasAnyRole(
                        "TELLER", "CUSTOMER_SERVICE_OFFICER", "BRANCH_MANAGER", "BANK_ADMIN", "SUPER_ADMIN"
                )
                // Specific customer detail views (protected by service-layer ownership / branch authorization)
                .requestMatchers("/customers/{id}", "/customers/account/**").authenticated()

                // Operational routes - require authentication
                .requestMatchers(
                        "/dashboard",
                        "/deposit",
                        "/withdraw",
                        "/transfer",
                        "/transactions/**"
                ).authenticated()

                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(accessDeniedHandler)
            )
            .sessionManagement(session -> session
                .maximumSessions(2)
                .maxSessionsPreventsLogin(false)
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(content -> {})
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );

        return http.build();
    }
}
