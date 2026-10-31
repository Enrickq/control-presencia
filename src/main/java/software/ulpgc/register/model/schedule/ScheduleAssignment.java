package software.ulpgc.register.model.schedule;

import java.time.LocalDate;
import java.util.Objects;

public record ScheduleAssignment(
        String employeeId,
        String scheduleId,
        LocalDate validFrom,
        LocalDate validThrough
) {

    public ScheduleAssignment {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        if (scheduleId == null || scheduleId.isBlank()) {
            throw new IllegalArgumentException(
                    "Schedule id is required"
            );
        }

        Objects.requireNonNull(validFrom);
        Objects.requireNonNull(validThrough);

        if (validThrough.isBefore(validFrom)) {
            throw new IllegalArgumentException(
                    "Assignment end cannot precede start"
            );
        }
    }

    public boolean isActiveOn(LocalDate date) {
        Objects.requireNonNull(date);

        return !date.isBefore(validFrom)
                && !date.isAfter(validThrough);
    }
}