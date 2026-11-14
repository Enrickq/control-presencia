
package software.ulpgc.register.application.usecase;

import org.junit.jupiter.api.Test;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.application.port.out.ScheduleAssignmentRepository;
import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.schedule.ContinuousSchedule;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;
import software.ulpgc.register.domain.model.schedule.TimeSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministrationUseCasesTest {

    private static final String EMPLOYEE_ID = "EMP-001";
    private static final String SCHEDULE_ID = "OFFICE-001";

    private static final LocalDate NOVEMBER_1 =
            LocalDate.of(2026, 11, 1);

    private static final LocalDate NOVEMBER_15 =
            LocalDate.of(2026, 11, 15);

    private static final LocalDate NOVEMBER_16 =
            LocalDate.of(2026, 11, 16);

    private static final LocalDate NOVEMBER_20 =
            LocalDate.of(2026, 11, 20);

    private static final LocalDate NOVEMBER_30 =
            LocalDate.of(2026, 11, 30);

    private final InMemoryEmployeeRepository employees =
            new InMemoryEmployeeRepository();

    private final InMemoryScheduleRepository schedules =
            new InMemoryScheduleRepository();

    private final InMemoryAssignmentRepository assignments =
            new InMemoryAssignmentRepository();

    @Test
    void shouldRegisterEmployee() {
        Employee employee = new RegisterEmployee(employees)
                .execute(EMPLOYEE_ID, "Example Employee");

        assertThat(employee.id()).isEqualTo(EMPLOYEE_ID);
        assertThat(employee.name()).isEqualTo("Example Employee");

        assertThat(employees.findById(EMPLOYEE_ID))
                .contains(employee);
    }

    @Test
    void shouldRejectDuplicateEmployee() {
        RegisterEmployee register = new RegisterEmployee(employees);

        register.execute(EMPLOYEE_ID, "Example Employee");

        assertThatThrownBy(() ->
                register.execute(EMPLOYEE_ID, "Another Employee")
        ).isInstanceOf(IllegalStateException.class);

        assertThat(employees.findById(EMPLOYEE_ID))
                .get()
                .extracting(Employee::name)
                .isEqualTo("Example Employee");
    }

    @Test
    void shouldRejectEmployeeWithoutName() {
        RegisterEmployee register = new RegisterEmployee(employees);

        assertThatThrownBy(() ->
                register.execute(EMPLOYEE_ID, " ")
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(employees.findById(EMPLOYEE_ID)).isEmpty();
    }

    @Test
    void shouldRegisterSchedule() {
        Schedule schedule = officeSchedule();

        Schedule result = new RegisterSchedule(schedules)
                .execute(schedule);

        assertThat(result).isEqualTo(schedule);

        assertThat(schedules.findById(SCHEDULE_ID))
                .contains(schedule);
    }

    @Test
    void shouldRejectDuplicateSchedule() {
        RegisterSchedule register = new RegisterSchedule(schedules);

        register.execute(officeSchedule());

        assertThatThrownBy(() ->
                register.execute(officeSchedule())
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAssignScheduleToExistingEmployee() {
        registerEmployeeAndSchedule();

        ScheduleAssignment assignment = assign(
                NOVEMBER_1,
                NOVEMBER_15
        );

        assertThat(assignment.employeeId())
                .isEqualTo(EMPLOYEE_ID);

        assertThat(assignment.scheduleId())
                .isEqualTo(SCHEDULE_ID);

        assertThat(assignments.findByEmployeeId(EMPLOYEE_ID))
                .containsExactly(assignment);
    }

    @Test
    void shouldRejectAssignmentForUnknownEmployee() {
        new RegisterSchedule(schedules).execute(officeSchedule());

        assertThatThrownBy(() ->
                assign(NOVEMBER_1, NOVEMBER_15)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(assignments.findByEmployeeId(EMPLOYEE_ID))
                .isEmpty();
    }

    @Test
    void shouldRejectAssignmentForUnknownSchedule() {
        new RegisterEmployee(employees)
                .execute(EMPLOYEE_ID, "Example Employee");

        assertThatThrownBy(() ->
                assign(NOVEMBER_1, NOVEMBER_15)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(assignments.findByEmployeeId(EMPLOYEE_ID))
                .isEmpty();
    }

    @Test
    void shouldRejectOverlappingAssignments() {
        registerEmployeeAndSchedule();

        ScheduleAssignment original = assign(
                NOVEMBER_1,
                NOVEMBER_15
        );

        assertThatThrownBy(() ->
                assign(NOVEMBER_15, NOVEMBER_20)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(assignments.findByEmployeeId(EMPLOYEE_ID))
                .containsExactly(original);
    }

    @Test
    void shouldAllowConsecutiveNonOverlappingAssignments() {
        registerEmployeeAndSchedule();

        ScheduleAssignment first = assign(
                NOVEMBER_1,
                NOVEMBER_15
        );

        ScheduleAssignment second = assign(
                NOVEMBER_16,
                NOVEMBER_30
        );

        assertThat(assignments.findByEmployeeId(EMPLOYEE_ID))
                .containsExactly(first, second);
    }

    private void registerEmployeeAndSchedule() {
        new RegisterEmployee(employees)
                .execute(EMPLOYEE_ID, "Example Employee");

        new RegisterSchedule(schedules)
                .execute(officeSchedule());
    }

    private ScheduleAssignment assign(
            LocalDate from,
            LocalDate through
    ) {
        return new AssignScheduleToEmployee(
                employees,
                schedules,
                assignments
        ).execute(
                EMPLOYEE_ID,
                SCHEDULE_ID,
                from,
                through
        );
    }

    private Schedule officeSchedule() {
        return new ContinuousSchedule(
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
        );
    }

    private static class InMemoryEmployeeRepository
            implements EmployeeRepository {

        private final Map<String, Employee> employees =
                new HashMap<>();

        @Override
        public void save(Employee employee) {
            employees.put(employee.id(), employee);
        }

        @Override
        public Optional<Employee> findById(String employeeId) {
            return Optional.ofNullable(employees.get(employeeId));
        }
    }

    private static class InMemoryScheduleRepository
            implements ScheduleRepository {

        private final Map<String, Schedule> schedules =
                new HashMap<>();

        @Override
        public void save(Schedule schedule) {
            schedules.put(schedule.id(), schedule);
        }

        @Override
        public Optional<Schedule> findById(String scheduleId) {
            return Optional.ofNullable(schedules.get(scheduleId));
        }
    }

    private static class InMemoryAssignmentRepository
            implements ScheduleAssignmentRepository {

        private final List<ScheduleAssignment> assignments =
                new ArrayList<>();

        @Override
        public void save(ScheduleAssignment assignment) {
            assignments.add(assignment);
        }

        @Override
        public List<ScheduleAssignment> findByEmployeeId(
                String employeeId
        ) {
            return assignments.stream()
                    .filter(assignment ->
                            assignment.employeeId()
                                    .equals(employeeId))
                    .toList();
        }
    }
}
