package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.schedule.Schedule;

public interface RegisterScheduleCommand {
    Schedule execute(Schedule schedule);
}