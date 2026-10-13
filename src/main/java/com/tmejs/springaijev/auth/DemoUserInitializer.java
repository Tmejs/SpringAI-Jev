package com.tmejs.springaijev.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoUserInitializer {
    @Bean
    ApplicationRunner seedDemoUsers(AppUserRepository users, PasswordEncoder passwords,
            @Value("${app.demo.user-password:}") String userPassword,
            @Value("${app.demo.maintainer-password:}") String maintainerPassword) {
        return args -> {
            seed(users, passwords, "demo-user", userPassword, AppUserRole.USER);
            seed(users, passwords, "demo-maintainer", maintainerPassword, AppUserRole.MAINTAINER);
        };
    }

    private void seed(AppUserRepository users, PasswordEncoder passwords,
            String username, String password, AppUserRole role) {
        if (!password.isBlank() && users.findByUsername(username).isEmpty()) {
            users.save(new AppUser(username, passwords.encode(password), role));
        }
    }
}
