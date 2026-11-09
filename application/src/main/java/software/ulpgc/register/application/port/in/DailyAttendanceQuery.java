package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.attendance.AttendanceSummary;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.time.LocalDate;

public interface DailyAttendanceQuery {

    AttendanceSummary execute(
            String employeeId,
            LocalDate date,
            Schedule schedule,
            ScheduleAssignment assignment
    );
}