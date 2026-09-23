package com.ishan.sciverse.summit.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportUser {
    private String fullName;
    private String username;
    private String email;
    private String phoneNumber;
    private String gender;
    private String role;
    private String password;
    private String rawPassword;
    private List<ImportSession> sessions = new ArrayList<>();
}
