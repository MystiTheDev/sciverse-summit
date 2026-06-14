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
}
