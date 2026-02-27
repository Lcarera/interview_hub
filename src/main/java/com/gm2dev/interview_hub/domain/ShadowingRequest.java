package com.gm2dev.interview_hub.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "shadowing_requests", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShadowingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnoreProperties("shadowingRequests")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shadower_id", nullable = false)
    private Profile shadower;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShadowingRequestStatus status;

    private String reason;
}
