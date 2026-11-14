
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.AssignScheduleCommand;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.application.port.out.ScheduleAssignmentRepository;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.time.LocalDate;
import java.util.Objects;

public record AssignScheduleToEmployee(
        EmployeeRepository employees,
        ScheduleRepository schedules,
        ScheduleAssignmentRepository assignments
) implements AssignScheduleCommand {


    public AssignScheduleToEmployee {
        Objects.requireNonNull(employees);
        Objects.requireNonNull(schedules);
        Objects.requireNonNull(assignments);
    }

    @Override
    public ScheduleAssignment execute(
            String employeeId,
            String scheduleId,
            LocalDate validFrom,
            LocalDate validThrough
    ) {
        ScheduleAssignment assignment =
                new ScheduleAssignment(
                        employeeId,
                        scheduleId,
                        validFrom,
                        validThrough
                );

        if (employees.findById(employeeId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Employee does not exist"
            );
        }

        if (schedules.findById(scheduleId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Schedule does not exist"
            );
        }

        boolean overlaps = assignments
                .findByEmployeeId(employeeId)
                .stream()
                .anyMatch(existing ->
                        !existing.validFrom()
                                .isAfter(validThrough)
                                &&
                                !existing.validThrough()
                                        .isBefore(validFrom)
                );

        if (overlaps) {
            throw new IllegalStateException(
                    "Schedule assignments overlap"
            );
        }

        assignments.save(assignment);

        return assignment;
    }
}
