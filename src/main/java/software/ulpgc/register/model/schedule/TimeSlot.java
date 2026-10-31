package software.ulpgc.register.model.schedule;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);

        if (start.equals(end)) {
            throw new IllegalArgumentException(
                    "Start and end times must be different"
            );
        }
    }

    public boolean crossesMidnight() {
        return end.isBefore(start);
    }

    public long minutes() {
        long duration = Duration.between(start, end).toMinutes();

        return crossesMidnight()
                ? duration + Duration.ofDays(1).toMinutes()
                : duration;
    }
}