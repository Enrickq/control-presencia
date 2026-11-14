
package software.ulpgc.register.application.port.out;

import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.util.List;

public interface ScheduleAssignmentRepository {

    void save(ScheduleAssignment assignment);

    List<ScheduleAssignment> findByEmployeeId(
            String employeeId
    );
}
