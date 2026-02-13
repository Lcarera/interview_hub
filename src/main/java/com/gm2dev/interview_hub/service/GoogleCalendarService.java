package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleCalendarService {

    private final TokenEncryptionService tokenEncryptionService;
    private final ProfileRepository profileRepository;
    private final GoogleOAuthProperties googleProperties;

    public GoogleCalendarService(TokenEncryptionService tokenEncryptionService,
                                  ProfileRepository profileRepository,
                                  GoogleOAuthProperties googleProperties) {
        this.tokenEncryptionService = tokenEncryptionService;
        this.profileRepository = profileRepository;
        this.googleProperties = googleProperties;
    }

    public String createEvent(Profile interviewer, Interview interview) throws IOException {
        Calendar calendar = buildCalendarClient(interviewer);
        String calendarId = getCalendarId(interviewer);
        Event event = buildEvent(interview);

        Event created = calendar.events().insert(calendarId, event).execute();
        log.debug("Created Google Calendar event: {}", created.getId());
        return created.getId();
    }

    public void updateEvent(Profile interviewer, Interview interview) throws IOException {
        Calendar calendar = buildCalendarClient(interviewer);
        String calendarId = getCalendarId(interviewer);
        Event event = buildEvent(interview);

        calendar.events().update(calendarId, interview.getGoogleEventId(), event).execute();
        log.debug("Updated Google Calendar event: {}", interview.getGoogleEventId());
    }

    public void deleteEvent(Profile interviewer, String googleEventId) throws IOException {
        Calendar calendar = buildCalendarClient(interviewer);
        String calendarId = getCalendarId(interviewer);

        calendar.events().delete(calendarId, googleEventId).execute();
        log.debug("Deleted Google Calendar event: {}", googleEventId);
    }

    public void addAttendee(Profile interviewer, String googleEventId, String attendeeEmail) throws IOException {
        Calendar calendar = buildCalendarClient(interviewer);
        String calendarId = getCalendarId(interviewer);

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            attendees.addAll(event.getAttendees());
        }
        EventAttendee newAttendee = new EventAttendee().setEmail(attendeeEmail);
        attendees.add(newAttendee);

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Added attendee {} to event {}", attendeeEmail, googleEventId);
    }

    Calendar buildCalendarClient(Profile interviewer) throws IOException {
        String accessToken = tokenEncryptionService.decrypt(interviewer.getGoogleAccessToken());
        String refreshToken = tokenEncryptionService.decrypt(interviewer.getGoogleRefreshToken());

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(googleProperties.getClientId())
                .setClientSecret(googleProperties.getClientSecret())
                .setRefreshToken(refreshToken)
                .setAccessToken(new AccessToken(accessToken, Date.from(interviewer.getGoogleTokenExpiry())))
                .build();

        // Refresh token if expired
        if (interviewer.getGoogleTokenExpiry() != null &&
                interviewer.getGoogleTokenExpiry().isBefore(java.time.Instant.now())) {
            credentials.refresh();
            AccessToken newToken = credentials.getAccessToken();
            interviewer.setGoogleAccessToken(tokenEncryptionService.encrypt(newToken.getTokenValue()));
            interviewer.setGoogleTokenExpiry(newToken.getExpirationTime().toInstant());
            profileRepository.save(interviewer);
            log.debug("Refreshed Google access token for profile: {}", interviewer.getId());
        }

        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to create HTTP transport", e);
        }

        return new Calendar.Builder(transport, JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Interview Hub")
                .build();
    }

    private Event buildEvent(Interview interview) {
        Event event = new Event();

        String candidateName = extractCandidateName(interview.getCandidateInfo());
        event.setSummary(interview.getTechStack() + " Interview - " + candidateName);

        StringBuilder description = new StringBuilder();
        description.append("Tech Stack: ").append(interview.getTechStack());
        if (interview.getCandidateInfo() != null) {
            description.append("\nCandidate Info: ").append(interview.getCandidateInfo());
        }
        event.setDescription(description.toString());

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(interview.getStartTime())));
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(interview.getEndTime())));

        event.setStart(start);
        event.setEnd(end);

        return event;
    }

    private String getCalendarId(Profile interviewer) {
        return interviewer.getCalendarEmail() != null ? interviewer.getCalendarEmail() : "primary";
    }

    private String extractCandidateName(Map<String, Object> candidateInfo) {
        if (candidateInfo == null) {
            return "Unknown";
        }
        Object name = candidateInfo.get("name");
        return name != null ? name.toString() : "Unknown";
    }
}
