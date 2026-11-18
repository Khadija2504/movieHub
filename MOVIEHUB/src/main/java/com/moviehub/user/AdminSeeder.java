package com.moviehub.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.config.AppProperties;

import lombok.RequiredArgsConstructor;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD if it does not exist yet.
 * Credentials stay in the environment, never in a migration file.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AppProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = User.normalizeEmail(props.admin().email());
        String password = props.admin().password();

        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("ADMIN_PASSWORD not set: skipping admin account seeding");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(password), "Admin", "MovieHub", Role.ADMIN));
        log.info("Created admin account {}", email);
    }
}
