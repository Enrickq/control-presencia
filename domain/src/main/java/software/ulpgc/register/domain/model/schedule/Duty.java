package software.ulpgc.register.domain.model.schedule;

import java.time.LocalDate;
import java.util.Objects;

public record Duty(
        String employeeId,
        LocalDate startDay,
        TimeSlot slot,
        DutyMode mode
) {

    public Duty {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        Objects.requireNonNull(startDay);
        Objects.requireNonNull(slot);
        Objects.requireNonNull(mode);
    }

    public LocalDate endDay() {
        return slot.crossesMidnight()
                ? startDay.plusDays(1)
                : startDay;
    }

    public long expectedPresenceMinutes() {
        return mode == DutyMode.ON_SITE
                ? slot.minutes()
                : 0;
    }
}