
package software.ulpgc.register.infrastructure.adapter.in.http;

import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import software.ulpgc.register.application.port.in.AdministrationQuery;
import software.ulpgc.register.application.port.in.AssignScheduleCommand;
import software.ulpgc.register.application.port.in.RegisterEmployeeCommand;
import software.ulpgc.register.application.port.in.RegisterScheduleCommand;

import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.schedule.*;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class AdministrationHandlers {

    private final RegisterEmployeeCommand registerEmployee;
    private final RegisterScheduleCommand registerSchedule;
    private final AssignScheduleCommand assignSchedule;
    private final AdministrationQuery query;

    public AdministrationHandlers(
            RegisterEmployeeCommand registerEmployee,
            RegisterScheduleCommand registerSchedule,
            AssignScheduleCommand assignSchedule,
            AdministrationQuery query
    ) {
        this.registerEmployee =
                Objects.requireNonNull(registerEmployee);
        this.registerSchedule =
                Objects.requireNonNull(registerSchedule);
        this.assignSchedule =
                Objects.requireNonNull(assignSchedule);
        this.query = Objects.requireNonNull(query);
    }

    public record EmployeeDto(String id, String name) {}

    public record SlotDto(String start, String end) {}

    public record ScheduleDto(
            String type,
            String id,
            String name,
            SlotDto slot,
            List<SlotDto> slots,
            List<String> workingDays,
            Map<String, SlotDto> weeklySlots
    ) {}

    public record AssignmentDto(
            String employeeId,
            String scheduleId,
            String validFrom,
            String validThrough
    ) {}

    public void createEmployee(Context ctx) {
        EmployeeDto request = ctx.bodyAsClass(EmployeeDto.class);

        if (request == null) {
            throw new IllegalArgumentException(
                    "Employee body is required"
            );
        }

        Employee saved = registerEmployee.execute(
                request.id(),
                request.name()
        );

        ctx.status(201);
        ctx.json(new EmployeeDto(saved.id(), saved.name()));
    }

    public void getEmployee(Context ctx) {
        Employee employee = query.employeeById(
                ctx.pathParam("id")
        ).orElseThrow(() ->
                new NotFoundResponse("Employee not found")
        );

        ctx.json(new EmployeeDto(
                employee.id(),
                employee.name()
        ));
    }

    public void createSchedule(Context ctx) {
        ScheduleDto request = ctx.bodyAsClass(ScheduleDto.class);

        Schedule saved = registerSchedule.execute(
                fromDto(request)
        );

        ctx.status(201);
        ctx.json(toDto(saved));
    }

    public void getSchedule(Context ctx) {
        Schedule schedule = query.scheduleById(
                ctx.pathParam("id")
        ).orElseThrow(() ->
                new NotFoundResponse("Schedule not found")
        );

        ctx.json(toDto(schedule));
    }

    public void createAssignment(Context ctx) {
        AssignmentDto request =
                ctx.bodyAsClass(AssignmentDto.class);

        if (request == null) {
            throw new IllegalArgumentException(
                    "Assignment body is required"
            );
        }

        ScheduleAssignment saved = assignSchedule.execute(
                request.employeeId(),
                request.scheduleId(),
                parseDate(request.validFrom()),
                parseDate(request.validThrough())
        );

        ctx.status(201);
        ctx.json(toDto(saved));
    }

    public void getAssignments(Context ctx) {
        String employeeId = ctx.pathParam("id");

        if (query.employeeById(employeeId).isEmpty()) {
            throw new NotFoundResponse("Employee not found");
        }

        ctx.json(query.assignmentsOf(employeeId).stream()
                .map(AdministrationHandlers::toDto)
                .toList());
    }

    private static Schedule fromDto(ScheduleDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException(
                    "Schedule body is required"
            );
        }

        String type = required(dto.type(), "type")
                .toUpperCase(Locale.ROOT);

        return switch (type) {
            case "CONTINUOUS" -> new ContinuousSchedule(
                    dto.id(),
                    dto.name(),
                    fromSlot(dto.slot()),
                    fromDays(dto.workingDays())
            );

            case "SPLIT" -> new SplitSchedule(
                    dto.id(),
                    dto.name(),
                    fromSlots(dto.slots()),
                    fromDays(dto.workingDays())
            );

            case "SHIFT" -> new ShiftSchedule(
                    dto.id(),
                    dto.name(),
                    fromWeeklySlots(dto.weeklySlots())
            );

            default -> throw new IllegalArgumentException(
                    "Unsupported schedule type: " + type
            );
        };
    }

    private static ScheduleDto toDto(Schedule schedule) {
        if (schedule instanceof ContinuousSchedule s) {
            return new ScheduleDto(
                    "CONTINUOUS",
                    s.id(),
                    s.name(),
                    toSlot(s.slot()),
                    null,
                    toDays(s.workingDays()),
                    null
            );
        }

        if (schedule instanceof SplitSchedule s) {
            return new ScheduleDto(
                    "SPLIT",
                    s.id(),
                    s.name(),
                    null,
                    s.slots().stream()
                            .map(AdministrationHandlers::toSlot)
                            .toList(),
                    toDays(s.workingDays()),
                    null
            );
        }

        if (schedule instanceof ShiftSchedule s) {
            Map<String, SlotDto> weekly = new TreeMap<>();

            s.weeklySlots().forEach((day, slot) ->
                    weekly.put(day.name(), toSlot(slot))
            );

            return new ScheduleDto(
                    "SHIFT",
                    s.id(),
                    s.name(),
                    null,
                    null,
                    null,
                    weekly
            );
        }

        throw new IllegalArgumentException(
                "Unsupported schedule type"
        );
    }

    private static AssignmentDto toDto(
            ScheduleAssignment assignment
    ) {
        return new AssignmentDto(
                assignment.employeeId(),
                assignment.scheduleId(),
                assignment.validFrom().toString(),
                assignment.validThrough().toString()
        );
    }

    private static TimeSlot fromSlot(SlotDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException(
                    "Schedule slot is required"
            );
        }

        try {
            return new TimeSlot(
                    LocalTime.parse(required(dto.start(), "start")),
                    LocalTime.parse(required(dto.end(), "end"))
            );
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "Invalid time. Expected HH:mm", e
            );
        }
    }

    private static SlotDto toSlot(TimeSlot slot) {
        return new SlotDto(
                slot.start().toString(),
                slot.end().toString()
        );
    }

    private static List<TimeSlot> fromSlots(
            List<SlotDto> slots
    ) {
        if (slots == null) {
            throw new IllegalArgumentException(
                    "Schedule slots are required"
            );
        }

        return slots.stream()
                .map(AdministrationHandlers::fromSlot)
                .toList();
    }

    private static EnumSet<DayOfWeek> fromDays(
            List<String> days
    ) {
        if (days == null) {
            throw new IllegalArgumentException(
                    "Working days are required"
            );
        }

        EnumSet<DayOfWeek> result =
                EnumSet.noneOf(DayOfWeek.class);

        for (String day : days) {
            result.add(parseDay(day));
        }

        return result;
    }

    private static List<String> toDays(
            java.util.Set<DayOfWeek> days
    ) {
        return days.stream()
                .sorted()
                .map(DayOfWeek::name)
                .toList();
    }

    private static Map<DayOfWeek, TimeSlot> fromWeeklySlots(
            Map<String, SlotDto> slots
    ) {
        if (slots == null) {
            throw new IllegalArgumentException(
                    "Weekly slots are required"
            );
        }

        Map<DayOfWeek, TimeSlot> result =
                new EnumMap<>(DayOfWeek.class);

        slots.forEach((day, slot) ->
                result.put(
                        parseDay(day),
                        fromSlot(slot)
                )
        );

        return result;
    }

    private static DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(
                    required(value, "day")
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid working day: " + value, e
            );
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(required(value, "date"));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "Invalid date. Expected YYYY-MM-DD", e
            );
        }
    }

    private static String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        return value.trim();
    }
}
