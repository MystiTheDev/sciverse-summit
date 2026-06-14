package com.ishan.sciverse.summit.data;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Presentation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;
	private boolean presenting;
	private boolean voting;
	
	// Link to session
	@ManyToOne
	@JoinColumn(name = "session_id")
	private com.ishan.sciverse.summit.entity.Session session;
	
	// Stats fields
	private int timesSpoken;
	private long totalSpeakingTime; // in seconds
	private int motionProposals;
	private int amendmentProposals;

	// Evaluation score fields (total max = 100)
	private int sciKnowledge;           // max 25
	private int representationAccuracy; // max 15
	private int publicSpeaking;         // max 15
	private int participation;          // max 15
	private int resolutionDrafting;     // max 15
	private int collaboration;          // max 10
	private int leadershipMatrix;       // max 5
	
	

	// A constructor for easy testing/initial data
	public Presentation(String name, boolean presenting, boolean voting) {
		this.name = name;
		this.presenting = presenting;
		this.voting = voting;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isPresenting() {
		return presenting;
	}

	public void setPresenting(boolean presenting) {
		this.presenting = presenting;
	}

	public boolean isVoting() {
		return voting;
	}

	public void setVoting(boolean voting) {
		this.voting = voting;
	}
	
	public String getFormattedSpeakingTime() {
		long minutes = totalSpeakingTime / 60;
		long seconds = totalSpeakingTime % 60;
		return String.format("%d:%02d", minutes, seconds);
	}

	public int getMatrixScore() {
		return sciKnowledge + representationAccuracy + publicSpeaking
				+ participation + resolutionDrafting + collaboration + leadershipMatrix;
	}

	public int getSciKnowledge() { return sciKnowledge; }
	public void setSciKnowledge(int sciKnowledge) { this.sciKnowledge = Math.min(sciKnowledge, 25); }

	public int getRepresentationAccuracy() { return representationAccuracy; }
	public void setRepresentationAccuracy(int representationAccuracy) { this.representationAccuracy = Math.min(representationAccuracy, 15); }

	public int getPublicSpeaking() { return publicSpeaking; }
	public void setPublicSpeaking(int publicSpeaking) { this.publicSpeaking = Math.min(publicSpeaking, 15); }

	public int getParticipation() { return participation; }
	public void setParticipation(int participation) { this.participation = Math.min(participation, 15); }

	public int getResolutionDrafting() { return resolutionDrafting; }
	public void setResolutionDrafting(int resolutionDrafting) { this.resolutionDrafting = Math.min(resolutionDrafting, 15); }

	public int getCollaboration() { return collaboration; }
	public void setCollaboration(int collaboration) { this.collaboration = Math.min(collaboration, 10); }

	public int getLeadershipMatrix() { return leadershipMatrix; }
	public void setLeadershipMatrix(int leadershipMatrix) { this.leadershipMatrix = Math.min(leadershipMatrix, 5); }

}
