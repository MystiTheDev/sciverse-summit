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

    public void saveUser(User user) {
        user.setRawPassword(user.getPassword()); // store plain text before encoding
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER"); // Default role
        userRepository.save(user);
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
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) return false;
            user.setRawPassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
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

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
