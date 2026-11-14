
package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.util.List;
import java.util.Optional;

public interface AdministrationQuery {

    Optional<Employee> employeeById(String id);

    Optional<Schedule> scheduleById(String id);

    List<ScheduleAssignment> assignmentsOf(String employeeId);
}
