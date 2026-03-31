package com.gm2dev.shared.calendar;

/**
 * Returned by calendar-service after creating or updating an event.
 * meetLink may be null if no conference was created.
 */
public record CalendarEventResponse(String eventId, String meetLink) {}
