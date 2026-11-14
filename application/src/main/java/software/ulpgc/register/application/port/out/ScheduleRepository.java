
package software.ulpgc.register.application.port.out;

import software.ulpgc.register.domain.model.schedule.Schedule;

import java.util.Optional;

public interface ScheduleRepository {

    void save(Schedule schedule);

    Optional<Schedule> findById(String scheduleId);
}
