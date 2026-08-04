package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String login(@org.springframework.web.bind.annotation.RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Incorrect username, email or password");
        }
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "register";
        }

        if (userService.isUsernameTaken(user.getUsername())) {
            model.addAttribute("registrationError", "This username already exists. Try another one.");
            return "register";
        }

        if (userService.isEmailTaken(user.getEmail())) {
            model.addAttribute("registrationError", "This email already exists. Try another one.");
            return "register";
        }

        userService.saveUser(user);
        return "redirect:/login?success";
    }

    // ── Account Recovery (public endpoint) ─────────────────────────────────
    @PostMapping("/api/forgot-password")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> forgotPassword(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String username,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String email) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        if (username.isBlank() && email.isBlank()) {
            response.put("found", false);
            response.put("message", "Please provide at least a username or email.");
            return response;
        }
        return userService.lookupForForgotPassword(username, email).map(u -> {
            java.util.Map<String, Object> r = new java.util.HashMap<>();
            r.put("found",    true);
            r.put("fullName", u.getFullName()    != null ? u.getFullName()    : "");
            r.put("username", u.getUsername());
            r.put("email",    u.getEmail());
            r.put("password", u.getRawPassword() != null ? u.getRawPassword()
                    : "(password was set before account recovery was enabled)");
            return r;
        }).orElseGet(() -> {
            java.util.Map<String, Object> r = new java.util.HashMap<>();
            r.put("found",   false);
            r.put("message", "No account found matching the provided details.");
            return r;
        });
    }

    // ── Change Password (authenticated endpoint) ────────────────────────────
    @PostMapping("/api/settings/change-password")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> changePassword(
            @org.springframework.web.bind.annotation.RequestParam String currentPassword,
            @org.springframework.web.bind.annotation.RequestParam String newPassword,
            @org.springframework.web.bind.annotation.RequestParam String confirmPassword,
            java.security.Principal principal) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        if (principal == null) {
            response.put("success", false); response.put("message", "Not authenticated."); return response;
        }
        if (!newPassword.equals(confirmPassword)) {
            response.put("success", false); response.put("message", "New passwords do not match."); return response;
        }
        if (newPassword.length() < 6) {
            response.put("success", false); response.put("message", "Password must be at least 6 characters."); return response;
        }
        boolean changed = userService.changePassword(principal.getName(), currentPassword, newPassword);
        response.put("success", changed);
        response.put("message", changed ? "Password updated successfully." : "Current password is incorrect.");
        return response;
    }
}
