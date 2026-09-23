package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.UserRepository;
import com.ishan.sciverse.summit.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @GetMapping("/admin/users")
    public String showUsers(Model model) {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("users", users);
        return "admin-users";
    }

    @PostMapping("/admin/users/update")
    public String updateRole(@RequestParam Long userId,
                             @RequestParam String role,
                             RedirectAttributes ra) {
        userService.updateRole(userId, role);
        ra.addFlashAttribute("message", "Role updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/delete")
    public String deleteUser(@RequestParam Long userId, RedirectAttributes ra) {
        userService.deleteUser(userId);
        ra.addFlashAttribute("message", "Account deleted.");
        return "redirect:/admin/users";
    }
}
