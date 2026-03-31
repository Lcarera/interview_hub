package com.gm2dev.shared.calendar;

/**
 * Sent by core to calendar-service to add or remove a single attendee from an event.
 */
public record AttendeeRequest(String googleEventId, String email) {}
