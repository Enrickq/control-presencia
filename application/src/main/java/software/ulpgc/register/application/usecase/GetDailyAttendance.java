
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.AttendanceReportQuery;
import software.ulpgc.register.application.port.in.DailyAttendanceQuery;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.domain.model.attendance.AttendanceSummary;

import java.time.LocalDate;
import java.util.Objects;

public record GetDailyAttendance(
        EmployeeRepository employees,
        ResolveDailySchedule resolver,
        DailyAttendanceQuery calculator
) implements AttendanceReportQuery {

    public GetDailyAttendance {
        Objects.requireNonNull(employees);
        Objects.requireNonNull(resolver);
        Objects.requireNonNull(calculator);
    }

    @Override
    public AttendanceSummary execute(
            String employeeId,
            LocalDate date
    ) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        Objects.requireNonNull(date, "Date is required");

        if (employees.findById(employeeId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Employee does not exist"
            );
        }

        ResolveDailySchedule.Resolution resolution =
                resolver.execute(employeeId, date)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No active schedule assignment"
                                )
                        );

        return calculator.execute(
                employeeId,
                date,
                resolution.schedule(),
                resolution.assignment()
        );
    }
}
