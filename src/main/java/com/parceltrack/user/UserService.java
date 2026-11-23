package com.parceltrack.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerSeller(String fullName, String email, String password) {
        validatePassword(password);
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        return createUser(fullName, normalizedEmail, password, Role.SELLER, false);
    }

    @Transactional
    public User createRider(String fullName, String email, String password) {
        validatePassword(password);
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        return createUser(fullName, normalizedEmail, password, Role.RIDER, true);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        User user = getByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("The current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
    }

    @Transactional
    public void setPassword(String email, String newPassword) {
        validatePassword(newPassword);
        User user = getByEmail(email);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
    }

    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private User createUser(String fullName, String email, String password, Role role,
                            boolean mustChangePassword) {
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(mustChangePassword);
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return email.trim().toLowerCase();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("Password must be at least 10 characters.");
        }
    }
}
