package com.parceltrack.delivery;

import com.parceltrack.user.Role;
import com.parceltrack.user.User;
import com.parceltrack.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Looks up the logged-in user and re-checks their role inside the service layer, so M3's
 * authorization does not depend only on URL rules or @PreAuthorize.
 */
@Component
class CurrentUsers {

    private final UserRepository userRepository;

    CurrentUsers(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    User require(String email, Role... allowed) {
        if (email == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
        if (!user.isEnabled()) {
            throw new AccessDeniedException("Account disabled");
        }
        for (Role role : allowed) {
            if (user.getRole() == role) {
                return user;
            }
        }
        throw new AccessDeniedException("Role not permitted");
    }

    Map<Long, String> namesById(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }
}
