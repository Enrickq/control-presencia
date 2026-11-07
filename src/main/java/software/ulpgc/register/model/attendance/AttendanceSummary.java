package software.ulpgc.register.model.attendance;

import java.time.LocalDate;
import java.util.Objects;

public record AttendanceSummary(
        String employeeId,
        LocalDate date,
        long expectedMinutes,
        long workedMinutes,
        long lateMinutes,
        AttendanceStatus status
) {

    public AttendanceSummary {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        Objects.requireNonNull(date);
        Objects.requireNonNull(status);

        if (expectedMinutes < 0
                || workedMinutes < 0
                || lateMinutes < 0) {
            throw new IllegalArgumentException(
                    "Attendance minutes cannot be negative"
            );
        }
    }

    public long balanceMinutes() {
        return workedMinutes - expectedMinutes;
    }
}