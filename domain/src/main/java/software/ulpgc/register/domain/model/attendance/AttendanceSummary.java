package software.ulpgc.register.domain.model.attendance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    public List<AttendanceIncident> incidents() {
        List<AttendanceIncident> incidents = new ArrayList<>();
        switch (status) {
            case ABSENT -> incidents.add(AttendanceIncident.ABSENCE);
            case INCOMPLETE -> incidents.add(AttendanceIncident.INCOMPLETE_PUNCHES);
            case UNSCHEDULED -> incidents.add(AttendanceIncident.UNSCHEDULED_WORK);
            case PRESENT, OFF -> { }
        }
        if (lateMinutes > 0) incidents.add(AttendanceIncident.LATE_ARRIVAL);
        return List.copyOf(incidents);
    }

    public long balanceMinutes() {
        return workedMinutes - expectedMinutes;
    }
}