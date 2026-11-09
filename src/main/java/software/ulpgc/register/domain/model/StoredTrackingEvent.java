package software.ulpgc.register.domain.model;

import java.time.Instant;

public record StoredTrackingEvent(
        Instant ts,
        String employeeId,
        String placeId,
        TrackingType type) {
}