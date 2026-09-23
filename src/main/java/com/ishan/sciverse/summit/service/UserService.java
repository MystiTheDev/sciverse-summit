package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditService auditService;

    public void saveUser(User user) {
        user.setRawPassword(user.getPassword()); // store plain text before encoding
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(user.getRole() != null && !user.getRole().isBlank() ? user.getRole().trim() : "DELEGATE");
        User saved = userRepository.save(user);
        auditService.log("USER_REGISTERED", "USER", saved.getId(),
                "Self-registered account \"" + saved.getUsername() + "\".");
    }

    public User createUser(String username, String email, String fullName, String password, String role) {
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setFullName(fullName);
        user.setPassword(password);
        user.setRawPassword(password);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(user);
        auditService.log("USER_CREATED", "USER", saved.getId(),
                "Created account \"" + saved.getUsername() + "\" with role " + saved.getRole() + ".");
        return saved;
    }

    public void updateRole(Long userId, String role) {
        userRepository.findById(userId).ifPresent(u -> {
            String normalized = normalizeRole(role);
            if (normalized != null) {
                String before = u.getRole();
                u.setRole(normalized);
                userRepository.save(u);
                auditService.log("USER_ROLE_CHANGED", "USER", userId,
                        "Changed role of \"" + u.getUsername() + "\" from " + before + " to " + normalized + ".");
            }
        });
    }

    public void deleteUser(Long userId) {
        String name = userRepository.findById(userId).map(User::getUsername).orElse(String.valueOf(userId));
        userRepository.deleteById(userId);
        auditService.log("USER_DELETED", "USER", userId, "Deleted account \"" + name + "\".");
    }

    public boolean hasChair() {
        return hasChairWithUsers(userRepository);
    }

    /** Only returns true when a user with role CHAIR exists (not the bootstrap ADMIN). */
    public boolean hasChairAccount() {
        return userRepository.findAll().stream()
                .anyMatch(u -> "CHAIR".equalsIgnoreCase(u.getRole()));
    }

    /** Static variant so it can be used without Spring injection. */
    public static boolean hasChairWithUsers(com.ishan.sciverse.summit.repository.UserRepository repo) {
        return repo.findAll().stream()
                .anyMatch(u -> isChairRole(u.getRole()));
    }

    public boolean isChair(String username) {
        return userRepository.findByUsername(username)
                .map(u -> isChairRole(u.getRole()))
                .orElse(false);
    }

    public static boolean isChairRole(String role) {
        return role != null && ("CHAIR".equalsIgnoreCase(role.trim())
                || "ADMIN".equalsIgnoreCase(role.trim())
                || "USER".equalsIgnoreCase(role.trim()));
    }

    /** Maps a stored role to its Spring Security role name (e.g. "DELEGATE" -> "ROLE_DELEGATE"). */
    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "DELEGATE";
        String r = role.trim().toUpperCase();
        if (r.startsWith("ROLE_")) r = r.substring(5);
        if (r.equals("USER")) return "CHAIR";
        return r;
    }

    public java.util.Optional<User> lookupForForgotPassword(String username, String email) {
        boolean hasU = username != null && !username.trim().isEmpty();
        boolean hasE = email    != null && !email.trim().isEmpty();
        if (!hasU && !hasE) return java.util.Optional.empty();
        if (hasU && hasE) {
            java.util.Optional<User> byU = userRepository.findByUsername(username.trim());
            if (byU.isPresent() && byU.get().getEmail().equalsIgnoreCase(email.trim())) return byU;
            java.util.Optional<User> byE = userRepository.findByEmail(email.trim());
            if (byE.isPresent() && byE.get().getUsername().equalsIgnoreCase(username.trim())) return byE;
            return java.util.Optional.empty();
        }
        return hasU ? userRepository.findByUsername(username.trim()) : userRepository.findByEmail(email.trim());
    }

    public boolean changePassword(String username, String currentPassword, String newPassword) {
        return userRepository.findByUsername(username).map(user -> {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                auditService.log("PASSWORD_CHANGE_FAILED", "USER", user.getId(),
                        "Password change rejected for \"" + username + "\" (wrong current password).");
                return false;
            }
            user.setRawPassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            auditService.log("PASSWORD_CHANGED", "USER", user.getId(),
                    "Password changed for \"" + username + "\".");
            return true;
        }).orElse(false);
    }

    public boolean isUsernameTaken(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public boolean isEmailTaken(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + usernameOrEmail));

        String role = normalizeRole(user.getRole());
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(role)
                .build();
    }
}
