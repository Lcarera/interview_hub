package com.gm2dev.interview_hub.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "candidates", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "primary_area")
    private String primaryArea;

    @Column(name = "feedback_link")
    private String feedbackLink;
}
