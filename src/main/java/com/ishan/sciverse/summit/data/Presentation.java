package com.ishan.sciverse.summit.data;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
	
	// Stats fields
	private int timesSpoken;
	private long totalSpeakingTime; // in seconds
	private int motionProposals;
	private int amendmentProposals;
	
	

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

}
