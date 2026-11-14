
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.AdministrationQuery;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.application.port.out.ScheduleAssignmentRepository;
import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ReadAdministrationData(
        EmployeeRepository employees,
        ScheduleRepository schedules,
        ScheduleAssignmentRepository assignments
) implements AdministrationQuery {

    public ReadAdministrationData {
        Objects.requireNonNull(employees);
        Objects.requireNonNull(schedules);
        Objects.requireNonNull(assignments);
    }

    @Override
    public Optional<Employee> employeeById(String id) {
        return employees.findById(id);
    }

    @Override
    public Optional<Schedule> scheduleById(String id) {
        return schedules.findById(id);
    }

    @Override
    public List<ScheduleAssignment> assignmentsOf(
            String employeeId
    ) {
        return assignments.findByEmployeeId(employeeId);
    }
}
