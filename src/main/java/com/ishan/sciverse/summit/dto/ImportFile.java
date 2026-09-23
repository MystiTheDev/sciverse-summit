package com.ishan.sciverse.summit.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportFile {
    private String app;
    private int exportVersion;
    private String exportedAt;
    private List<ImportUser> users = new ArrayList<>();
}
