package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import java.util.UUID;

@Value
@Schema(description = "Abbreviated shadowing request used in nested contexts")
public class ShadowingRequestSummaryDto {
    @Schema(description = "Shadowing request UUID")
    UUID id;

    @Schema(description = "Request status", example = "PENDING")
    ShadowingRequestStatus status;

    @Schema(description = "Profile of the shadower")
    ProfileDto shadower;
}
