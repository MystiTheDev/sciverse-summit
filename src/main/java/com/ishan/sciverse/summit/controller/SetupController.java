package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.repository.UserRepository;
import com.ishan.sciverse.summit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SetupController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.ishan.sciverse.summit.service.AuditService auditService;

    @GetMapping("/setup")
    public String showSetup(Model model) {
        model.addAttribute("error", null);
        return "setup";
    }

    /**
     * Creates the first chair account, then permanently deletes the bootstrap ADMIN account.
     * Safe-guarded so it can only ever run while there is no existing chair.
     */
    @PostMapping("/setup")
    public String createChair(@RequestParam String username,
                              @RequestParam String email,
                              @RequestParam String fullName,
                              @RequestParam String password,
                              @RequestParam String confirmPassword,
                              Model model,
                              HttpServletRequest request) {

        // Refuse to run if a CHAIR account already exists (the bootstrap ADMIN is not a chair yet)
        if (userService.hasChairAccount()) {
            return "redirect:/login?error=setupDone";
        }

        if (username == null || username.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            model.addAttribute("error", "All fields are required.");
            return "setup";
        }
        if (password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "setup";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "setup";
        }
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            model.addAttribute("error", "This username already exists.");
            return "setup";
        }
        if (userRepository.findByEmail(email.trim()).isPresent()) {
            model.addAttribute("error", "This email is already in use.");
            return "setup";
        }

        // 1. Create the chair account
        userService.createUser(username, email, fullName, password, "CHAIR");
        auditService.log("SETUP_COMPLETED", "USER", null,
                "Initial chair \"" + username.trim() + "\" created; bootstrap admin removed.");

        // 2. Permanently delete the bootstrap ADMIN account
        userRepository.findByUsername("admin").ifPresent(u -> userRepository.delete(u));
        userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .forEach(u -> userRepository.delete(u));

        // 3. Invalidate the admin's session so they can no longer use the deleted account
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();

        return "redirect:/login?chairCreated";
    }
}
