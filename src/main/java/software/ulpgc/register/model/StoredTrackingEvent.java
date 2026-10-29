package software.ulpgc.register.model;

import java.time.Instant;

public record StoredTrackingEvent(
        Instant ts,
        String employeeId,
        String placeId,
        TrackingType type) {
}