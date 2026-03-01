package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;

    public Profile findById(UUID id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found: " + id));
    }

    public List<Profile> findAll() {
        return profileRepository.findAll();
    }
}
