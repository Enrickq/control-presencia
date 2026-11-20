package software.ulpgc.register.infrastructure.adapter.in.http;

import io.javalin.http.Context;
import software.ulpgc.register.application.port.in.AttendanceReportQuery;
import software.ulpgc.register.domain.model.attendance.AttendanceSummary;

import software.ulpgc.register.domain.model.attendance.AttendanceIncident;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record AttendanceHandlers(
        AttendanceReportQuery reports
) {

    public AttendanceHandlers {
        Objects.requireNonNull(reports);
    }

    public void getDaily(Context ctx) {
        String employeeId = required(
                ctx.queryParam("employeeId"),
                "employeeId"
        );

        String dateText = required(
                ctx.queryParam("date"),
                "date"
        );

        LocalDate date = LocalDate.parse(dateText);

        AttendanceSummary summary =
                reports.execute(employeeId, date);

        ctx.json(new AttendanceResponse(
                summary.employeeId(),
                summary.date().toString(),
                summary.expectedMinutes(),
                summary.workedMinutes(),
                summary.lateMinutes(),
                summary.balanceMinutes(),
                summary.status().name(),
                summary.incidents()
        ));
    }

    private static String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        return value.trim();
    }

    private record AttendanceResponse(
            String employeeId,
            String date,
            long expectedMinutes,
            long workedMinutes,
            long lateMinutes,
            long balanceMinutes,
            String status,
            List<AttendanceIncident> incidents
    ) {
    }
}
