package software.ulpgc.register.application.usecase;

import org.junit.jupiter.api.Test;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.application.port.out.EventStore;
import software.ulpgc.register.application.port.out.ScheduleAssignmentRepository;
import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.StoredTrackingEvent;
import software.ulpgc.register.domain.model.TrackingEvent;
import software.ulpgc.register.domain.model.TrackingType;
import software.ulpgc.register.domain.model.attendance.AttendanceStatus;
import software.ulpgc.register.domain.model.attendance.AttendanceSummary;
import software.ulpgc.register.domain.model.schedule.ContinuousSchedule;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;
import software.ulpgc.register.domain.model.schedule.TimeSlot;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetDailyAttendanceTest {

    private static final String EMPLOYEE_ID = "EMP-001";
    private static final String SCHEDULE_ID = "OFFICE-001";

    private static final LocalDate FRIDAY =
            LocalDate.of(2026, 11, 20);

    private static final LocalDate SUNDAY =
            LocalDate.of(2026, 11, 22);

    private final Map<String, Employee> employees =
            new HashMap<>();

    private final Map<String, Schedule> schedules =
            new HashMap<>();

    private final List<ScheduleAssignment> assignments =
            new ArrayList<>();

    private final List<StoredTrackingEvent> events =
            new ArrayList<>();

    @Test
    void shouldCalculateAttendanceUsingActiveAssignment() {
        registerEmployeeAndSchedule();
        assign(FRIDAY, FRIDAY);

        events.add(event(
                "2026-11-20T08:10:00Z",
                TrackingType.In
        ));
        events.add(event(
                "2026-11-20T16:05:00Z",
                TrackingType.Out
        ));

        AttendanceSummary result =
                report().execute(EMPLOYEE_ID, FRIDAY);

        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.workedMinutes()).isEqualTo(475);
        assertThat(result.lateMinutes()).isEqualTo(10);
        assertThat(result.balanceMinutes()).isEqualTo(-5);
        assertThat(result.status())
                .isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void shouldDetectAbsence() {
        registerEmployeeAndSchedule();
        assign(FRIDAY, FRIDAY);

        AttendanceSummary result =
                report().execute(EMPLOYEE_ID, FRIDAY);

        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.workedMinutes()).isZero();
        assertThat(result.status())
                .isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void shouldIdentifyDayOff() {
        registerEmployeeAndSchedule();
        assign(FRIDAY, SUNDAY);

        AttendanceSummary result =
                report().execute(EMPLOYEE_ID, SUNDAY);

        assertThat(result.expectedMinutes()).isZero();
        assertThat(result.status())
                .isEqualTo(AttendanceStatus.OFF);
    }

    @Test
    void shouldAcceptAssignmentBoundaryDates() {
        registerEmployeeAndSchedule();
        assign(FRIDAY, FRIDAY);

        assertThat(report().execute(EMPLOYEE_ID, FRIDAY)
                .expectedMinutes()).isEqualTo(480);
    }

    @Test
    void shouldRejectUnknownEmployee() {
        assertThatThrownBy(() ->
                report().execute("UNKNOWN", FRIDAY)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Employee does not exist");
    }

    @Test
    void shouldRejectMissingAssignment() {
        registerEmployeeAndSchedule();

        assertThatThrownBy(() ->
                report().execute(EMPLOYEE_ID, FRIDAY)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No active schedule assignment");
    }

    @Test
    void shouldRejectOverlappingActiveAssignments() {
        registerEmployeeAndSchedule();
        assign(FRIDAY, SUNDAY);
        assign(FRIDAY.minusDays(1), FRIDAY);

        assertThatThrownBy(() ->
                report().execute(EMPLOYEE_ID, FRIDAY)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Multiple active schedule assignments");
    }

    @Test
    void shouldRejectAssignmentToMissingSchedule() {
        employees.put(
                EMPLOYEE_ID,
                new Employee(EMPLOYEE_ID, "Employee")
        );

        assignments.add(new ScheduleAssignment(
                EMPLOYEE_ID,
                "UNKNOWN-SCHEDULE",
                FRIDAY,
                FRIDAY
        ));

        assertThatThrownBy(() ->
                report().execute(EMPLOYEE_ID, FRIDAY)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Assigned schedule does not exist");
    }

    private void registerEmployeeAndSchedule() {
        employees.put(
                EMPLOYEE_ID,
                new Employee(EMPLOYEE_ID, "Employee")
        );

        schedules.put(
                SCHEDULE_ID,
                new ContinuousSchedule(
                        SCHEDULE_ID,
                        "Office",
                        new TimeSlot(
                                LocalTime.of(8, 0),
                                LocalTime.of(16, 0)
                        ),
                        Set.of(
                                DayOfWeek.MONDAY,
                                DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY,
                                DayOfWeek.FRIDAY
                        )
                )
        );
    }

    private void assign(LocalDate from, LocalDate through) {
        assignments.add(new ScheduleAssignment(
                EMPLOYEE_ID,
                SCHEDULE_ID,
                from,
                through
        ));
    }

    private GetDailyAttendance report() {
        EmployeeRepository employeeRepository =
                new EmployeeRepository() {
                    @Override
                    public void save(Employee employee) {
                        employees.put(employee.id(), employee);
                    }

                    @Override
                    public Optional<Employee> findById(String id) {
                        return Optional.ofNullable(employees.get(id));
                    }
                };

        ScheduleRepository scheduleRepository =
                new ScheduleRepository() {
                    @Override
                    public void save(Schedule schedule) {
                        schedules.put(schedule.id(), schedule);
                    }

                    @Override
                    public Optional<Schedule> findById(String id) {
                        return Optional.ofNullable(schedules.get(id));
                    }
                };

        ScheduleAssignmentRepository assignmentRepository =
                new ScheduleAssignmentRepository() {
                    @Override
                    public void save(ScheduleAssignment assignment) {
                        assignments.add(assignment);
                    }

                    @Override
                    public List<ScheduleAssignment> findByEmployeeId(
                            String employeeId
                    ) {
                        return assignments.stream()
                                .filter(a ->
                                        a.employeeId().equals(employeeId)
                                )
                                .toList();
                    }
                };

        EventStore eventStore = new EventStore() {
            @Override
            public void register(TrackingEvent event) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<StoredTrackingEvent> read(LocalDate date) {
                return events.stream()
                        .filter(e ->
                                LocalDate.ofInstant(
                                        e.ts(),
                                        ZoneOffset.UTC
                                ).equals(date)
                        )
                        .toList();
            }
        };

        return new GetDailyAttendance(
                employeeRepository,
                new ResolveDailySchedule(
                        assignmentRepository,
                        scheduleRepository
                ),
                new CalculateDailyAttendance(eventStore)
        );
    }

    private static StoredTrackingEvent event(
            String timestamp,
            TrackingType type
    ) {
        return new StoredTrackingEvent(
                Instant.parse(timestamp),
                EMPLOYEE_ID,
                "OFFICE",
                type
        );
    }
}
