package com.parceltrack.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(UserRepository userRepository, UserService userService,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("Admin User", "admin@parceltrack.local", "Admin12345!", Role.ADMIN, false);
        seed("Rider User", "rider@parceltrack.local", "Rider12345!", Role.RIDER, false);
        seed("Seller User", "seller@parceltrack.local", "Seller12345!", Role.SELLER, false);
    }

    private void seed(String fullName, String email, String password, Role role,
                      boolean mustChangePassword) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        if (role == Role.SELLER) {
            userService.registerSeller(fullName, email, password);
        } else if (role == Role.RIDER) {
            userService.createRider(fullName, email, password);
        } else {
            User user = new User();
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(role);
            user.setEnabled(true);
            user.setMustChangePassword(mustChangePassword);
            userRepository.save(user);
        }
    }
}
