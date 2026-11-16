package software.ulpgc.register.frontend.model;

import java.time.Instant;
import java.util.Objects;

public record PunchReceipt(String employeeId, String placeId, PunchType type, Instant timestamp) {
    public PunchReceipt {
        Objects.requireNonNull(employeeId);
        Objects.requireNonNull(placeId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(timestamp);
    }
}
