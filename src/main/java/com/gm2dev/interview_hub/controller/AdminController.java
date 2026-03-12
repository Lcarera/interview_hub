package com.gm2dev.interview_hub.controller;

import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.dto.CreateUserRequest;
import com.gm2dev.interview_hub.dto.UpdateRoleRequest;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.gm2dev.interview_hub.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('admin')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProfileRepository profileRepository;

    @GetMapping
    public ResponseEntity<Page<Profile>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(profileRepository.findAll(pageable));
    }

    @PostMapping
    public ResponseEntity<Profile> createUser(@Valid @RequestBody CreateUserRequest request) {
        Profile created = adminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        adminService.updateRole(id, request.role());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
