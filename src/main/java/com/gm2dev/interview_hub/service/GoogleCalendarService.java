package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleServiceAccountProperties;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.Profile;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class GoogleCalendarService {

    private final GoogleServiceAccountProperties serviceAccountProperties;

    public GoogleCalendarService(GoogleServiceAccountProperties serviceAccountProperties) {
        this.serviceAccountProperties = serviceAccountProperties;
    }

    public String createEvent(Profile interviewer, Interview interview) throws IOException {
        Calendar calendar = buildCalendarClient(interviewer);
        String calendarId = getCalendarId(interviewer);
        Event event = buildEvent(interviewer, interview);

        Event created = calendar.events().insert(calendarId, event)
                .setConferenceDataVersion(1)
                .execute();
        log.debug("Created Google Calendar event: {}", created.getId());
        return created.getId();
    }

    public void updateEvent(Profile interviewer, Interview interview) throws IOException {
        Calendar calendar = buildCalendarClient(interviewer);
        String calendarId = getCalendarId(interviewer);
        Event event = buildEvent(interviewer, interview);

        calendar.events().update(calendarId, interview.getGoogleEventId(), event)
                .setConferenceDataVersion(1)
                .execute();
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
        if (serviceAccountProperties.getKeyJson() == null || serviceAccountProperties.getKeyJson().isBlank()) {
            throw new IOException("Google service account key not configured");
        }

        GoogleCredentials credentials = ServiceAccountCredentials
                .fromStream(new java.io.ByteArrayInputStream(serviceAccountProperties.getKeyJson().getBytes()))
                .createScoped(java.util.List.of("https://www.googleapis.com/auth/calendar"))
                .createDelegated(interviewer.getCalendarEmail() != null
                        ? interviewer.getCalendarEmail()
                        : interviewer.getEmail());

        credentials.refreshIfExpired();

        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Failed to create HTTP transport", e);
        }

        return new Calendar.Builder(transport, JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Interview Hub")
                .build();
    }

    private Event buildEvent(Profile interviewer, Interview interview) {
        Event event = new Event();

        Candidate candidate = interview.getCandidate();
        String candidateName = candidate != null && candidate.getName() != null ? candidate.getName() : "Unknown";
        event.setSummary(interview.getTechStack() + " Interview - " + candidateName);

        StringBuilder description = new StringBuilder();
        description.append("Tech Stack: ").append(interview.getTechStack());

        if (candidate != null) {
            description.append("\n\nCandidate Details:");
            description.append("\n  Name: ").append(candidate.getName());
            if (candidate.getEmail() != null) {
                description.append("\n  Email: ").append(candidate.getEmail());
            }
            if (candidate.getLinkedinUrl() != null) {
                description.append("\n  LinkedIn: ").append(candidate.getLinkedinUrl());
            }
            if (candidate.getPrimaryArea() != null) {
                description.append("\n  Primary Area: ").append(candidate.getPrimaryArea());
            }
            if (candidate.getFeedbackLink() != null) {
                description.append("\n  Feedback Link: ").append(candidate.getFeedbackLink());
            }
        }
        event.setDescription(description.toString());

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(interview.getStartTime())));
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(interview.getEndTime())));

        event.setStart(start);
        event.setEnd(end);

        ConferenceSolutionKey solutionKey = new ConferenceSolutionKey().setType("hangoutsMeet");
        CreateConferenceRequest conferenceRequest = new CreateConferenceRequest()
                .setConferenceSolutionKey(solutionKey)
                .setRequestId(UUID.randomUUID().toString());
        event.setConferenceData(new ConferenceData().setCreateRequest(conferenceRequest));

        List<EventAttendee> attendees = new ArrayList<>();
        attendees.add(new EventAttendee().setEmail(interviewer.getEmail()));

        if (candidate != null && candidate.getEmail() != null) {
            attendees.add(new EventAttendee().setEmail(candidate.getEmail()));
        }
        event.setAttendees(attendees);

        return event;
    }

    private String getCalendarId(Profile interviewer) {
        return interviewer.getCalendarEmail() != null ? interviewer.getCalendarEmail() : "primary";
    }
}
