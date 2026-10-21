package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingType;

import java.time.Instant;

public record StoredTrackingEvent(
        Instant ts,
        String employeeId,
        String placeId,
        TrackingType type) {
}