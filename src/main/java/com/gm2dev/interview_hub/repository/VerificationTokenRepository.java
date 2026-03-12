package com.gm2dev.interview_hub.repository;

import com.gm2dev.interview_hub.domain.TokenType;
import com.gm2dev.interview_hub.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndTokenType(String token, TokenType tokenType);
}
