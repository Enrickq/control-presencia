
package software.ulpgc.register.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.schedule.ContinuousSchedule;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;
import software.ulpgc.register.domain.model.schedule.ShiftSchedule;
import software.ulpgc.register.domain.model.schedule.SplitSchedule;
import software.ulpgc.register.domain.model.schedule.TimeSlot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FileAdministrationRepositoriesTest {

    @TempDir
    Path directory;

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 16);

    private static final LocalDate TUESDAY =
            LocalDate.of(2026, 11, 17);

    @Test
    void shouldPersistAndReloadEmployee() {
        Path path = directory.resolve("employees.jsonl");

        Employee employee =
                new Employee("EMP-001", "Example Employee");

        new FileEmployeeRepository(path).save(employee);

        FileEmployeeRepository reopened =
                new FileEmployeeRepository(path);

        assertThat(reopened.findById("EMP-001"))
                .contains(employee);
    }

    @Test
    void shouldPersistContinuousSchedule() {
        Path path = directory.resolve("schedules.jsonl");

        Schedule schedule = new ContinuousSchedule(
                "OFFICE-001",
                "Office",
                slot(8, 0, 16, 0),
                Set.of(DayOfWeek.MONDAY)
        );

        new FileScheduleRepository(path).save(schedule);

        FileScheduleRepository reopened =
                new FileScheduleRepository(path);

        assertThat(reopened.findById("OFFICE-001"))
                .contains(schedule);

        assertThat(reopened.findById("OFFICE-001").orElseThrow()
                .expectedMinutesOn(MONDAY))
                .isEqualTo(480);
    }

    @Test
    void shouldPersistSplitSchedule() {
        Path path = directory.resolve("schedules.jsonl");

        Schedule schedule = new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(
                        slot(10, 0, 14, 0),
                        slot(18, 0, 22, 0)
                ),
                Set.of(DayOfWeek.MONDAY)
        );

        new FileScheduleRepository(path).save(schedule);

        Schedule restored =
                new FileScheduleRepository(path)
                        .findById("RESTAURANT-001")
                        .orElseThrow();

        assertThat(restored)
                .isInstanceOf(SplitSchedule.class);

        assertThat(restored.expectedMinutesOn(MONDAY))
                .isEqualTo(480);

        assertThat(restored.slotsOn(MONDAY))
                .containsExactly(
                        slot(10, 0, 14, 0),
                        slot(18, 0, 22, 0)
                );
    }

    @Test
    void shouldPersistOvernightShiftSchedule() {
        Path path = directory.resolve("schedules.jsonl");

        Schedule schedule = new ShiftSchedule(
                "HOSPITAL-001",
                "Hospital",
                Map.of(
                        DayOfWeek.TUESDAY,
                        slot(22, 0, 6, 0)
                )
        );

        new FileScheduleRepository(path).save(schedule);

        Schedule restored =
                new FileScheduleRepository(path)
                        .findById("HOSPITAL-001")
                        .orElseThrow();

        assertThat(restored)
                .isInstanceOf(ShiftSchedule.class);

        assertThat(restored.expectedMinutesOn(TUESDAY))
                .isEqualTo(480);

        assertThat(restored.slotsOn(TUESDAY).getFirst()
                .crossesMidnight())
                .isTrue();
    }

    @Test
    void shouldPersistScheduleAssignments() {
        Path path = directory.resolve("assignments.jsonl");

        ScheduleAssignment first = new ScheduleAssignment(
                "EMP-001",
                "OFFICE-001",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 15)
        );

        ScheduleAssignment second = new ScheduleAssignment(
                "EMP-001",
                "HOSPITAL-001",
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 30)
        );

        FileScheduleAssignmentRepository repository =
                new FileScheduleAssignmentRepository(path);

        repository.save(first);
        repository.save(second);

        FileScheduleAssignmentRepository reopened =
                new FileScheduleAssignmentRepository(path);

        assertThat(reopened.findByEmployeeId("EMP-001"))
                .containsExactly(first, second);
    }

    @Test
    void shouldReturnEmptyResultsForMissingFiles() {
        assertThat(new FileEmployeeRepository(
                directory.resolve("employees.jsonl")
        ).findById("UNKNOWN")).isEmpty();

        assertThat(new FileScheduleRepository(
                directory.resolve("schedules.jsonl")
        ).findById("UNKNOWN")).isEmpty();

        assertThat(new FileScheduleAssignmentRepository(
                directory.resolve("assignments.jsonl")
        ).findByEmployeeId("UNKNOWN")).isEmpty();
    }

    @Test
    void shouldWriteOneValidJsonObjectPerLine()
            throws Exception {

        Path path = directory.resolve("employees.jsonl");

        FileEmployeeRepository repository =
                new FileEmployeeRepository(path);

        repository.save(new Employee("EMP-001", "First"));
        repository.save(new Employee("EMP-002", "Second"));

        List<String> lines = Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        );

        assertThat(lines).hasSize(2);

        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.readTree(lines.get(0))
                .path("id").asText())
                .isEqualTo("EMP-001");

        assertThat(mapper.readTree(lines.get(1))
                .path("id").asText())
                .isEqualTo("EMP-002");
    }

    private static TimeSlot slot(
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return new TimeSlot(
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute)
        );
    }
}
