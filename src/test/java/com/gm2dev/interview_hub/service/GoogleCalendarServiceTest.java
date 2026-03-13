package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.config.GoogleServiceAccountProperties;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.repository.ProfileRepository;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-32chars-long!";

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private Calendar calendarClient;

    @Mock
    private Calendar.Events events;

    @Mock
    private Calendar.Events.Insert insert;

    @Mock
    private Calendar.Events.Update update;

    @Mock
    private Calendar.Events.Delete deleteOp;

    @Mock
    private Calendar.Events.Get getOp;

    @Mock
    private Calendar.Events.Patch patchOp;

    private TokenEncryptionService tokenEncryptionService;
    private GoogleCalendarService googleCalendarService;

    @BeforeEach
    void setUp() {
        tokenEncryptionService = new TokenEncryptionService(ENCRYPTION_KEY);
        GoogleOAuthProperties googleProps = new GoogleOAuthProperties();
        googleProps.setClientId("test-client-id");
        googleProps.setClientSecret("test-client-secret");

        GoogleServiceAccountProperties serviceAccountProps = new GoogleServiceAccountProperties();
        serviceAccountProps.setKeyJson("");

        googleCalendarService = spy(new GoogleCalendarService(
                tokenEncryptionService, profileRepository, googleProps, serviceAccountProps));
    }

    private Profile buildProfile() {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("interviewer@gm2dev.com");
        profile.setRole(Role.interviewer);
        profile.setGoogleAccessToken(tokenEncryptionService.encrypt("access-token"));
        profile.setGoogleRefreshToken(tokenEncryptionService.encrypt("refresh-token"));
        profile.setGoogleTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        return profile;
    }

    private Interview buildInterview() {
        Interview interview = new Interview();
        interview.setId(UUID.randomUUID());
        interview.setTechStack("Java");
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setName("Jane Doe");
        candidate.setEmail("jane@example.com");
        interview.setCandidate(candidate);
        interview.setStartTime(Instant.now().plus(1, ChronoUnit.DAYS));
        interview.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        interview.setStatus(InterviewStatus.SCHEDULED);
        return interview;
    }

    @Test
    void createEvent_returnsGoogleEventId() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("google-event-id-123");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        String eventId = googleCalendarService.createEvent(profile, interview);

        assertEquals("google-event-id-123", eventId);
        verify(events).insert(eq("primary"), any(Event.class));
    }

    @Test
    void createEvent_usesCalendarEmail_whenSet() throws IOException {
        Profile profile = buildProfile();
        profile.setCalendarEmail("team-calendar@gm2dev.com");
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-456");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("team-calendar@gm2dev.com"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        String eventId = googleCalendarService.createEvent(profile, interview);

        assertEquals("event-456", eventId);
        verify(events).insert(eq("team-calendar@gm2dev.com"), any(Event.class));
    }

    @Test
    void createEvent_buildsEventWithCorrectFields() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-789");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), eventCaptor.capture());
        Event event = eventCaptor.getValue();

        assertTrue(event.getSummary().contains("Java"));
        assertTrue(event.getSummary().contains("Jane Doe"));
        assertNotNull(event.getStart());
        assertNotNull(event.getEnd());
    }

    @Test
    void updateEvent_updatesExistingEvent() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();
        interview.setGoogleEventId("existing-event-id");

        Event updatedEvent = new Event().setId("existing-event-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("primary"), eq("existing-event-id"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(profile, interview);

        verify(events).update(eq("primary"), eq("existing-event-id"), any(Event.class));
    }

    @Test
    void deleteEvent_deletesEvent() throws IOException {
        Profile profile = buildProfile();

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.delete("primary", "event-to-delete")).thenReturn(deleteOp);

        googleCalendarService.deleteEvent(profile, "event-to-delete");

        verify(events).delete("primary", "event-to-delete");
        verify(deleteOp).execute();
    }

    @Test
    void createEvent_withNullCandidate_usesUnknownInSummary() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();
        interview.setCandidate(null);

        Event createdEvent = new Event().setId("event-null-candidate");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertTrue(event.getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_withCandidateNullName_usesUnknownInSummary() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setEmail("test@example.com");
        // name is null
        interview.setCandidate(candidate);

        Event createdEvent = new Event().setId("event-no-name");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertTrue(event.getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_formatsDescriptionCleanly() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-desc");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), captor.capture());
        String desc = captor.getValue().getDescription();

        assertTrue(desc.contains("Tech Stack: Java"));
        assertTrue(desc.contains("Candidate Details:"));
        assertTrue(desc.contains("Name: Jane Doe"));
        assertTrue(desc.contains("Email: jane@example.com"));
        assertFalse(desc.contains("{"), "Description should not contain raw map format");
    }

    @Test
    void createEvent_includesInterviewerAndCandidateAsAttendees() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-attendees");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(2, attendees.size());
        assertTrue(attendees.stream().anyMatch(a -> "interviewer@gm2dev.com".equals(a.getEmail())));
        assertTrue(attendees.stream().anyMatch(a -> "jane@example.com".equals(a.getEmail())));
    }

    @Test
    void createEvent_onlyInterviewerAttendee_whenNoCandidateEmail() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setName("Jane Doe");
        // email is null
        interview.setCandidate(candidate);

        Event createdEvent = new Event().setId("event-no-candidate-email");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(1, attendees.size());
        assertEquals("interviewer@gm2dev.com", attendees.get(0).getEmail());
    }

    @Test
    void addAttendee_addsEmailToEvent() throws IOException {
        Profile profile = buildProfile();

        Event existingEvent = new Event()
                .setId("event-with-attendees")
                .setAttendees(List.of());

        Event patchedEvent = new Event().setId("event-with-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.get("primary", "event-with-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("primary"), eq("event-with-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee(profile, "event-with-attendees", "shadower@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("primary"), eq("event-with-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertTrue(patched.getAttendees().stream()
                .anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void addAttendee_withNullAttendeesList_addsAttendee() throws IOException {
        Profile profile = buildProfile();

        Event existingEvent = new Event()
                .setId("event-null-attendees");
        // attendees is null by default

        Event patchedEvent = new Event().setId("event-null-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.get("primary", "event-null-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("primary"), eq("event-null-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee(profile, "event-null-attendees", "new@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("primary"), eq("event-null-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertEquals(1, patched.getAttendees().size());
        assertEquals("new@gm2dev.com", patched.getAttendees().get(0).getEmail());
    }

    @Test
    void createEvent_includesGoogleMeetConferenceData() throws IOException {
        Profile profile = buildProfile();
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-meet");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(profile, interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("primary"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertNotNull(event.getConferenceData());
        assertEquals("hangoutsMeet",
                event.getConferenceData().getCreateRequest().getConferenceSolutionKey().getType());
        assertNotNull(event.getConferenceData().getCreateRequest().getRequestId());
    }

    @Test
    void deleteEvent_usesCalendarEmail_whenSet() throws IOException {
        Profile profile = buildProfile();
        profile.setCalendarEmail("team@gm2dev.com");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient(profile);
        when(calendarClient.events()).thenReturn(events);
        when(events.delete("team@gm2dev.com", "event-del")).thenReturn(deleteOp);

        googleCalendarService.deleteEvent(profile, "event-del");

        verify(events).delete("team@gm2dev.com", "event-del");
    }
}
