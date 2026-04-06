package com.gm2dev.calendar_service;

import com.gm2dev.calendar_service.config.GoogleCalendarProperties;
import com.gm2dev.calendar_service.config.GoogleOAuthProperties;
import com.gm2dev.shared.calendar.AttendeeRequest;
import com.gm2dev.shared.calendar.CalendarEventRequest;
import com.gm2dev.shared.calendar.CalendarEventResponse;
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
import com.google.auth.oauth2.UserCredentials;
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

    private final GoogleOAuthProperties oAuthProperties;
    private final GoogleCalendarProperties calendarProperties;

    public GoogleCalendarService(GoogleOAuthProperties oAuthProperties,
                                  GoogleCalendarProperties calendarProperties) {
        this.oAuthProperties = oAuthProperties;
        this.calendarProperties = calendarProperties;
    }

    public CalendarEventResponse createEvent(CalendarEventRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        Event event = buildEvent(request);

        Event created = calendar.events().insert(calendarId, event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        log.debug("Created Google Calendar event: {}", created.getId());
        return new CalendarEventResponse(created.getId(), created.getHangoutLink());
    }

    public void updateEvent(CalendarEventRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        Event event = buildEvent(request);

        calendar.events().update(calendarId, request.googleEventId(), event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        log.debug("Updated Google Calendar event: {}", request.googleEventId());
    }

    public void deleteEvent(String googleEventId) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();

        calendar.events().delete(calendarId, googleEventId)
                .setSendUpdates("all")
                .execute();
        log.debug("Deleted Google Calendar event: {}", googleEventId);
    }

    public void addAttendee(AttendeeRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        String googleEventId = request.googleEventId();
        String attendeeEmail = request.email();

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            attendees.addAll(event.getAttendees());
        }
        attendees.add(new EventAttendee().setEmail(attendeeEmail));

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Added attendee {} to event {}", attendeeEmail, googleEventId);
    }

    public void removeAttendee(AttendeeRequest request) throws IOException {
        Calendar calendar = buildCalendarClient();
        String calendarId = calendarProperties.getId();
        String googleEventId = request.googleEventId();
        String attendeeEmail = request.email();

        Event event = calendar.events().get(calendarId, googleEventId).execute();

        List<EventAttendee> attendees = new ArrayList<>();
        if (event.getAttendees() != null) {
            event.getAttendees().stream()
                    .filter(a -> !attendeeEmail.equals(a.getEmail()))
                    .forEach(attendees::add);
        }

        Event patch = new Event().setAttendees(attendees);
        calendar.events().patch(calendarId, googleEventId, patch)
                .setSendUpdates("all")
                .execute();

        log.debug("Removed attendee {} from event {}", attendeeEmail, googleEventId);
    }

    Calendar buildCalendarClient() throws IOException {
        if (calendarProperties.getRefreshToken() == null || calendarProperties.getRefreshToken().isBlank()) {
            throw new IOException("Google Calendar refresh token not configured");
        }

        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(oAuthProperties.getClientId())
                .setClientSecret(oAuthProperties.getClientSecret())
                .setRefreshToken(calendarProperties.getRefreshToken())
                .build();

        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Failed to create HTTP transport", e);
        }

        return new Calendar.Builder(transport, JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Interview Hub - Calendar Service")
                .build();
    }

    private Event buildEvent(CalendarEventRequest request) {
        Event event = new Event();

        String candidateName = request.candidateName() != null ? request.candidateName() : "Unknown";
        event.setSummary(request.techStack() + " Interview - " + candidateName);

        StringBuilder description = new StringBuilder();
        description.append("Tech Stack: ").append(request.techStack());

        description.append("\n\nCandidate Details:");
        description.append("\n  Name: ").append(candidateName);
        if (request.candidateEmail() != null) {
            description.append("\n  Email: ").append(request.candidateEmail());
        }
        if (request.candidateLinkedIn() != null) {
            description.append("\n  LinkedIn: ").append(request.candidateLinkedIn());
        }
        if (request.primaryArea() != null) {
            description.append("\n  Primary Area: ").append(request.primaryArea());
        }
        if (request.feedbackLink() != null) {
            description.append("\n  Feedback Link: ").append(request.feedbackLink());
        }

        event.setDescription(description.toString());

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(request.startTime())));
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(Date.from(request.endTime())));

        event.setStart(start);
        event.setEnd(end);

        ConferenceSolutionKey solutionKey = new ConferenceSolutionKey().setType("hangoutsMeet");
        CreateConferenceRequest conferenceRequest = new CreateConferenceRequest()
                .setConferenceSolutionKey(solutionKey)
                .setRequestId(UUID.randomUUID().toString());
        event.setConferenceData(new ConferenceData().setCreateRequest(conferenceRequest));

        List<EventAttendee> attendees = new ArrayList<>();

        if (request.interviewerEmail() != null) {
            attendees.add(new EventAttendee().setEmail(request.interviewerEmail()));
        }
        if (request.candidateEmail() != null) {
            attendees.add(new EventAttendee().setEmail(request.candidateEmail()));
        }
        if (request.approvedShadowerEmails() != null) {
            request.approvedShadowerEmails().stream()
                    .filter(email -> email != null)
                    .map(email -> new EventAttendee().setEmail(email))
                    .forEach(attendees::add);
        }

        event.setAttendees(attendees);

        return event;
    }
}
