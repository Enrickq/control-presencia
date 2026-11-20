package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.out.ScheduleAssignmentRepository;
import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ResolveDailySchedule(
        ScheduleAssignmentRepository assignments,
        ScheduleRepository schedules
) {

    public ResolveDailySchedule {
        Objects.requireNonNull(assignments);
        Objects.requireNonNull(schedules);
    }

    public record Resolution(
            ScheduleAssignment assignment,
            Schedule schedule
    ) {
    }

    public Optional<Resolution> execute(
            String employeeId,
            LocalDate date
    ) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        Objects.requireNonNull(date, "Date is required");

        List<ScheduleAssignment> active = assignments
                .findByEmployeeId(employeeId)
                .stream()
                .filter(assignment ->
                        !date.isBefore(assignment.validFrom())
                                && !date.isAfter(assignment.validThrough())
                )
                .toList();

        if (active.isEmpty()) {
            return Optional.empty();
        }

        if (active.size() > 1) {
            throw new IllegalStateException(
                    "Multiple active schedule assignments"
            );
        }

        ScheduleAssignment assignment = active.getFirst();

        Schedule schedule = schedules
                .findById(assignment.scheduleId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Assigned schedule does not exist"
                        )
                );

        return Optional.of(
                new Resolution(assignment, schedule)
        );
    }
}
