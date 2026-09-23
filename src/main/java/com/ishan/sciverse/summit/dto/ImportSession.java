package com.ishan.sciverse.summit.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportSession {
    private String name;
    private String committee;
    private int strength;
    private String topic;
    private LocalDateTime createdAt;
    private Boolean active;
    private String notes;
    private String ebReview;
    private String joinCode;
    private List<ImportDelegate> delegates = new ArrayList<>();
}
