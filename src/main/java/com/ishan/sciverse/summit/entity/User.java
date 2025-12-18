package com.ishan.sciverse.summit.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Username is required")
    @jakarta.persistence.Column(unique = true)
    private String username;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @jakarta.persistence.Column(unique = true)
    private String email;

    private String phoneNumber;

    @NotBlank(message = "Password is required")
    private String password;

    private String gender;

    private String role;
}
