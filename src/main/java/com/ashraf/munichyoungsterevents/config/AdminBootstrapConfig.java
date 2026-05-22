package com.ashraf.munichyoungsterevents.config;

import com.ashraf.munichyoungsterevents.entity.Role;
import com.ashraf.munichyoungsterevents.entity.User;
import com.ashraf.munichyoungsterevents.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class AdminBootstrapConfig {

    @Bean
    public ApplicationRunner adminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.email:}") String adminEmail,
            @Value("${app.admin.bootstrap.password:}") String adminPassword
    ) {
        return args -> {
            if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
                return;
            }

            if (userRepository.existsByEmail(adminEmail)) {
                return;
            }

            User adminUser = new User();
            adminUser.setEmail(adminEmail);
            adminUser.setFirstName("Admin");
            adminUser.setLastName("User");
            adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
            adminUser.setRole(Role.ADMIN);
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
        };
    }
}
