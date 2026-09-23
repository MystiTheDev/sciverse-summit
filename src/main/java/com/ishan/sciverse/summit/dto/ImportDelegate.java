package com.ishan.sciverse.summit.dto;

import lombok.Data;

@Data
public class ImportDelegate {
    private String name;
    private boolean presenting;
    private boolean voting;
    private int timesSpoken;
    private long totalSpeakingTime;
    private int motionProposals;
    private int amendmentProposals;
    private int sciKnowledge;
    private int representationAccuracy;
    private int publicSpeaking;
    private int participation;
    private int resolutionDrafting;
    private int collaboration;
    private int leadershipMatrix;
}
