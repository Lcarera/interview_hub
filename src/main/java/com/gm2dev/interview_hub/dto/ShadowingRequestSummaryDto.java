package com.gm2dev.interview_hub.dto;

import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
import lombok.Value;
import java.util.UUID;

@Value
public class ShadowingRequestSummaryDto {
    UUID id;
    ShadowingRequestStatus status;
    ProfileDto shadower;
}
