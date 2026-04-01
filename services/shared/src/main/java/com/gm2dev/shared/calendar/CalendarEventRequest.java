package com.gm2dev.shared.calendar;

import java.time.Instant;
import java.util.List;

/**
 * Sent by core to calendar-service when creating or updating an interview event.
 * googleEventId is null for create operations, required for update operations.
 */
public record CalendarEventRequest(
        String googleEventId,
        String techStack,
        String candidateName,
        String candidateEmail,
        String candidateLinkedIn,
        String primaryArea,
        String feedbackLink,
        String interviewerEmail,
        List<String> approvedShadowerEmails,
        Instant startTime,
        Instant endTime
) {}
