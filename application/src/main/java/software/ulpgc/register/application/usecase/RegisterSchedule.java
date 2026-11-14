
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.RegisterScheduleCommand;
import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.domain.model.schedule.Schedule;

import java.util.Objects;

public record RegisterSchedule(
        ScheduleRepository schedules
) implements RegisterScheduleCommand {

    public RegisterSchedule {
        Objects.requireNonNull(schedules);
    }

    @Override
    public Schedule execute(Schedule schedule) {
        Objects.requireNonNull(schedule);

        if (schedules.findById(schedule.id()).isPresent()) {
            throw new IllegalStateException(
                    "Schedule already exists"
            );
        }

        schedules.save(schedule);

        return schedule;
    }
}
