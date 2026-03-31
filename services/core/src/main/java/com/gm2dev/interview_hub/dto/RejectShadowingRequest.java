package com.gm2dev.interview_hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for rejecting a shadowing request")
public class RejectShadowingRequest {

    @Schema(description = "Reason for rejection", example = "Interview is already at full capacity")
    private String reason;
}
