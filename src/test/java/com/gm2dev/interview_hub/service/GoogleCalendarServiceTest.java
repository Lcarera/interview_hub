package com.gm2dev.interview_hub.service;

import com.gm2dev.interview_hub.config.GoogleCalendarProperties;
import com.gm2dev.interview_hub.config.GoogleOAuthProperties;
import com.gm2dev.interview_hub.domain.Candidate;
import com.gm2dev.interview_hub.domain.Interview;
import com.gm2dev.interview_hub.domain.InterviewStatus;
import com.gm2dev.interview_hub.domain.Profile;
import com.gm2dev.interview_hub.domain.Role;
import com.gm2dev.interview_hub.domain.ShadowingRequest;
import com.gm2dev.interview_hub.domain.ShadowingRequestStatus;
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

    private GoogleCalendarService googleCalendarService;
    private GoogleCalendarProperties calendarProperties;
    private GoogleOAuthProperties oAuthProperties;

    @BeforeEach
    void setUp() {
        calendarProperties = new GoogleCalendarProperties();
        calendarProperties.setId("test-calendar-id");
        calendarProperties.setRefreshToken("test-refresh-token");

        oAuthProperties = new GoogleOAuthProperties();
        oAuthProperties.setClientId("test-client-id");
        oAuthProperties.setClientSecret("test-client-secret");

        googleCalendarService = spy(new GoogleCalendarService(oAuthProperties, calendarProperties));
    }

    private Interview buildInterview() {
        Profile interviewer = new Profile();
        interviewer.setId(UUID.randomUUID());
        interviewer.setEmail("interviewer@gm2dev.com");
        interviewer.setRole(Role.interviewer);

        Interview interview = new Interview();
        interview.setId(UUID.randomUUID());
        interview.setInterviewer(interviewer);
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
    void buildCalendarClient_throwsWhenRefreshTokenBlank() {
        calendarProperties.setRefreshToken("");
        GoogleCalendarService bareService = new GoogleCalendarService(oAuthProperties, calendarProperties);
        assertThrows(IOException.class, bareService::buildCalendarClient);
    }

    @Test
    void createEvent_returnsGoogleEventId() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("google-event-id-123");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        GoogleCalendarService.CalendarEventResult result = googleCalendarService.createEvent(interview);

        assertEquals("google-event-id-123", result.eventId());
        verify(events).insert(eq("test-calendar-id"), any(Event.class));
    }

    @Test
    void createEvent_usesSendUpdatesAll() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-send-updates");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        verify(insert).setSendUpdates("all");
    }

    @Test
    void createEvent_usesConfiguredCalendarId() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-cal-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        verify(events).insert(eq("test-calendar-id"), any(Event.class));
    }

    @Test
    void createEvent_buildsEventWithCorrectFields() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-789");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();

        assertTrue(event.getSummary().contains("Java"));
        assertTrue(event.getSummary().contains("Jane Doe"));
        assertNotNull(event.getStart());
        assertNotNull(event.getEnd());
    }

    @Test
    void updateEvent_updatesExistingEvent() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("existing-event-id");

        Event updatedEvent = new Event().setId("existing-event-id");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("existing-event-id"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        verify(events).update(eq("test-calendar-id"), eq("existing-event-id"), any(Event.class));
    }

    @Test
    void updateEvent_usesSendUpdatesAll() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("event-update-sends");

        Event updatedEvent = new Event().setId("event-update-sends");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-update-sends"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        verify(update).setSendUpdates("all");
    }

    @Test
    void updateEvent_includesApprovedShadowerAsAttendee() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("event-with-shadower");

        Profile shadower = new Profile();
        shadower.setId(UUID.randomUUID());
        shadower.setEmail("shadower@gm2dev.com");
        shadower.setRole(Role.interviewer);

        ShadowingRequest approvedRequest = new ShadowingRequest();
        approvedRequest.setId(UUID.randomUUID());
        approvedRequest.setShadower(shadower);
        approvedRequest.setStatus(ShadowingRequestStatus.APPROVED);
        approvedRequest.setInterview(interview);
        interview.getShadowingRequests().add(approvedRequest);

        Event updatedEvent = new Event().setId("event-with-shadower");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-with-shadower"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).update(eq("test-calendar-id"), eq("event-with-shadower"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertTrue(attendees.stream().anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void updateEvent_doesNotIncludePendingShadowerAsAttendee() throws IOException {
        Interview interview = buildInterview();
        interview.setGoogleEventId("event-pending-shadow");

        Profile shadower = new Profile();
        shadower.setId(UUID.randomUUID());
        shadower.setEmail("pending@gm2dev.com");
        shadower.setRole(Role.interviewer);

        ShadowingRequest pendingRequest = new ShadowingRequest();
        pendingRequest.setId(UUID.randomUUID());
        pendingRequest.setShadower(shadower);
        pendingRequest.setStatus(ShadowingRequestStatus.PENDING);
        pendingRequest.setInterview(interview);
        interview.getShadowingRequests().add(pendingRequest);

        Event updatedEvent = new Event().setId("event-pending-shadow");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.update(eq("test-calendar-id"), eq("event-pending-shadow"), any(Event.class))).thenReturn(update);
        when(update.setConferenceDataVersion(1)).thenReturn(update);
        when(update.setSendUpdates("all")).thenReturn(update);
        when(update.execute()).thenReturn(updatedEvent);

        googleCalendarService.updateEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).update(eq("test-calendar-id"), eq("event-pending-shadow"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertFalse(attendees.stream().anyMatch(a -> "pending@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void deleteEvent_deletesEvent() throws IOException {
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.delete("test-calendar-id", "event-to-delete")).thenReturn(deleteOp);
        when(deleteOp.setSendUpdates("all")).thenReturn(deleteOp);

        googleCalendarService.deleteEvent("event-to-delete");

        verify(events).delete("test-calendar-id", "event-to-delete");
        verify(deleteOp).setSendUpdates("all");
        verify(deleteOp).execute();
    }

    @Test
    void createEvent_withNullCandidate_usesUnknownInSummary() throws IOException {
        Interview interview = buildInterview();
        interview.setCandidate(null);

        Event createdEvent = new Event().setId("event-null-candidate");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertTrue(event.getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_withCandidateNullName_usesUnknownInSummary() throws IOException {
        Interview interview = buildInterview();
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setEmail("test@example.com");
        interview.setCandidate(candidate);

        Event createdEvent = new Event().setId("event-no-name");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertTrue(event.getSummary().contains("Unknown"));
    }

    @Test
    void createEvent_formatsDescriptionCleanly() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-desc");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        String desc = captor.getValue().getDescription();

        assertTrue(desc.contains("Tech Stack: Java"));
        assertTrue(desc.contains("Candidate Details:"));
        assertTrue(desc.contains("Name: Jane Doe"));
        assertTrue(desc.contains("Email: jane@example.com"));
        assertFalse(desc.contains("{"), "Description should not contain raw map format");
    }

    @Test
    void createEvent_includesInterviewerAndCandidateAsAttendees() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-attendees");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(2, attendees.size());
        assertTrue(attendees.stream().anyMatch(a -> "interviewer@gm2dev.com".equals(a.getEmail())));
        assertTrue(attendees.stream().anyMatch(a -> "jane@example.com".equals(a.getEmail())));
    }

    @Test
    void createEvent_onlyInterviewerAttendee_whenNoCandidateEmail() throws IOException {
        Interview interview = buildInterview();
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setName("Jane Doe");
        interview.setCandidate(candidate);

        Event createdEvent = new Event().setId("event-no-candidate-email");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), captor.capture());
        List<EventAttendee> attendees = captor.getValue().getAttendees();

        assertEquals(1, attendees.size());
        assertEquals("interviewer@gm2dev.com", attendees.get(0).getEmail());
    }

    @Test
    void addAttendee_addsEmailToEvent() throws IOException {
        Event existingEvent = new Event()
                .setId("event-with-attendees")
                .setAttendees(List.of());

        Event patchedEvent = new Event().setId("event-with-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-with-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-with-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee("event-with-attendees", "shadower@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-with-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertTrue(patched.getAttendees().stream()
                .anyMatch(a -> "shadower@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void addAttendee_withNullAttendeesList_addsAttendee() throws IOException {
        Event existingEvent = new Event().setId("event-null-attendees");

        Event patchedEvent = new Event().setId("event-null-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-null-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-null-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.addAttendee("event-null-attendees", "new@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-null-attendees"), eventCaptor.capture());
        Event patched = eventCaptor.getValue();
        assertEquals(1, patched.getAttendees().size());
        assertEquals("new@gm2dev.com", patched.getAttendees().get(0).getEmail());
    }

    @Test
    void removeAttendee_removesEmailFromEvent() throws IOException {
        Event existingEvent = new Event()
                .setId("event-to-patch")
                .setAttendees(List.of(
                        new EventAttendee().setEmail("keep@gm2dev.com"),
                        new EventAttendee().setEmail("remove@gm2dev.com")
                ));
        Event patchedEvent = new Event().setId("event-to-patch");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-to-patch")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-to-patch"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.removeAttendee("event-to-patch", "remove@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-to-patch"), eventCaptor.capture());
        List<EventAttendee> attendees = eventCaptor.getValue().getAttendees();
        assertEquals(1, attendees.size());
        assertEquals("keep@gm2dev.com", attendees.get(0).getEmail());
        assertFalse(attendees.stream().anyMatch(a -> "remove@gm2dev.com".equals(a.getEmail())));
    }

    @Test
    void removeAttendee_whenNullAttendees_setsEmptyList() throws IOException {
        Event existingEvent = new Event().setId("event-no-attendees");

        Event patchedEvent = new Event().setId("event-no-attendees");

        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.get("test-calendar-id", "event-no-attendees")).thenReturn(getOp);
        when(getOp.execute()).thenReturn(existingEvent);
        when(events.patch(eq("test-calendar-id"), eq("event-no-attendees"), any(Event.class))).thenReturn(patchOp);
        when(patchOp.setSendUpdates("all")).thenReturn(patchOp);
        when(patchOp.execute()).thenReturn(patchedEvent);

        googleCalendarService.removeAttendee("event-no-attendees", "ghost@gm2dev.com");

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).patch(eq("test-calendar-id"), eq("event-no-attendees"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue().getAttendees().isEmpty());
    }

    @Test
    void createEvent_includesGoogleMeetConferenceData() throws IOException {
        Interview interview = buildInterview();

        Event createdEvent = new Event().setId("event-meet");
        doReturn(calendarClient).when(googleCalendarService).buildCalendarClient();
        when(calendarClient.events()).thenReturn(events);
        when(events.insert(eq("test-calendar-id"), any(Event.class))).thenReturn(insert);
        when(insert.setConferenceDataVersion(1)).thenReturn(insert);
        when(insert.setSendUpdates("all")).thenReturn(insert);
        when(insert.execute()).thenReturn(createdEvent);

        googleCalendarService.createEvent(interview);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(events).insert(eq("test-calendar-id"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertNotNull(event.getConferenceData());
        assertEquals("hangoutsMeet",
                event.getConferenceData().getCreateRequest().getConferenceSolutionKey().getType());
        assertNotNull(event.getConferenceData().getCreateRequest().getRequestId());
    }
}
