package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;
import java.time.LocalDate;

public interface AssignScheduleCommand {

    ScheduleAssignment execute(
            String employeeId,
            String scheduleId,
            LocalDate validFrom,
            LocalDate validThrough
    );
}