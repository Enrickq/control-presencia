package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.attendance.AttendanceSummary;

import java.time.LocalDate;

public interface AttendanceReportQuery {

    AttendanceSummary execute(
            String employeeId,
            LocalDate date
    );
}
