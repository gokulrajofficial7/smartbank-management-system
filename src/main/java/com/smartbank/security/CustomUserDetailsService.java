package com.smartbank.security;

import com.smartbank.entity.User;
import com.smartbank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // Auto-unlock if lockout duration has passed (15 mins)
        if (Boolean.FALSE.equals(user.getAccountNonLocked()) && user.getLockoutTime() != null) {
            if (user.getLockoutTime().isBefore(LocalDateTime.now())) {
                user.setAccountNonLocked(true);
                user.setFailedLoginAttempts(0);
                user.setLockoutTime(null);
                userRepository.save(user);
                log.info("Account auto-unlocked for user: {}", username);
            }
        }

        return new CustomUserDetails(user);
    }
}
