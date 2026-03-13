package com.gm2dev.interview_hub.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles", schema = "public")
@Data
@NoArgsConstructor
public class Profile {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "calendar_email")
    private String calendarEmail;

    @JsonIgnore
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @JsonIgnore
    @Column(name = "google_access_token")
    private String googleAccessToken;

    @JsonIgnore
    @Column(name = "google_refresh_token")
    private String googleRefreshToken;

    @JsonIgnore
    @Column(name = "google_token_expiry")
    private Instant googleTokenExpiry;

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    public Profile(UUID id, String email, Role role, String calendarEmail) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.calendarEmail = calendarEmail;
    }
}
