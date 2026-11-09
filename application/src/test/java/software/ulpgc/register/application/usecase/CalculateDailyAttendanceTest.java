package software.ulpgc.register.application.usecase;

import org.junit.jupiter.api.Test;

import software.ulpgc.register.application.port.out.EventStore;
import software.ulpgc.register.domain.model.StoredTrackingEvent;
import software.ulpgc.register.domain.model.TrackingEvent;
import software.ulpgc.register.domain.model.TrackingType;
import software.ulpgc.register.domain.model.attendance.AttendanceStatus;
import software.ulpgc.register.domain.model.attendance.AttendanceSummary;
import software.ulpgc.register.domain.model.schedule.ContinuousSchedule;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;
import software.ulpgc.register.domain.model.schedule.ShiftSchedule;
import software.ulpgc.register.domain.model.schedule.SplitSchedule;
import software.ulpgc.register.domain.model.schedule.TimeSlot;


import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculateDailyAttendanceTest {

    private static final String EMPLOYEE = "EMP-001";

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 2);

    private static final LocalDate TUESDAY =
            LocalDate.of(2026, 11, 3);

    private static final LocalDate SUNDAY =
            LocalDate.of(2026, 11, 8);

    @Test
    void shouldCalculateWorkedMinutesAndBalance() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule(),
                event("2026-11-02T08:10:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-02T16:05:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.workedMinutes()).isEqualTo(475);
        assertThat(result.lateMinutes()).isEqualTo(10);
        assertThat(result.balanceMinutes()).isEqualTo(-5);
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void shouldDetectAbsence() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule()
        );

        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.workedMinutes()).isZero();
        assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void shouldIdentifyDayOff() {
        AttendanceSummary result = calculate(
                SUNDAY,
                officeSchedule()
        );

        assertThat(result.expectedMinutes()).isZero();
        assertThat(result.status()).isEqualTo(AttendanceStatus.OFF);
    }

    @Test
    void shouldCalculateOvernightAttendance() {
        Schedule schedule = new ShiftSchedule(
                "NIGHT-001",
                "Night shift",
                Map.of(
                        DayOfWeek.TUESDAY,
                        slot(22, 0, 6, 0)
                )
        );

        AttendanceSummary result = calculate(
                TUESDAY,
                schedule,
                event("2026-11-03T22:00:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-04T06:00:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.balanceMinutes()).isZero();
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void shouldCalculateSplitShiftAndLateMinutes() {
        Schedule schedule = new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(
                        slot(10, 0, 14, 0),
                        slot(18, 0, 22, 0)
                ),
                Set.of(DayOfWeek.MONDAY)
        );

        AttendanceSummary result = calculate(
                MONDAY,
                schedule,
                event("2026-11-02T10:05:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-02T14:00:00Z", EMPLOYEE, TrackingType.Out),
                event("2026-11-02T18:20:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-02T22:10:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.workedMinutes()).isEqualTo(465);
        assertThat(result.lateMinutes()).isEqualTo(25);
        assertThat(result.balanceMinutes()).isEqualTo(-15);
    }

    @Test
    void shouldMarkOpenEntryAsIncomplete() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule(),
                event("2026-11-02T08:00:00Z", EMPLOYEE, TrackingType.In)
        );

        assertThat(result.status())
                .isEqualTo(AttendanceStatus.INCOMPLETE);
    }

    @Test
    void shouldIgnoreOtherEmployeesEvents() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule(),
                event("2026-11-02T08:00:00Z", "OTHER", TrackingType.In),
                event("2026-11-02T16:00:00Z", "OTHER", TrackingType.Out)
        );

        assertThat(result.status())
                .isEqualTo(AttendanceStatus.ABSENT);

        assertThat(result.workedMinutes()).isZero();
    }

    @Test
    void shouldRejectAssignmentForAnotherEmployee() {
        Schedule schedule = officeSchedule();

        ScheduleAssignment assignment = new ScheduleAssignment(
                "OTHER",
                schedule.id(),
                MONDAY,
                MONDAY
        );

        CalculateDailyAttendance calculator =
                new CalculateDailyAttendance(store());

        assertThatThrownBy(() -> calculator.execute(
                EMPLOYEE,
                MONDAY,
                schedule,
                assignment
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInactiveAssignment() {
        Schedule schedule = officeSchedule();

        ScheduleAssignment assignment = new ScheduleAssignment(
                EMPLOYEE,
                schedule.id(),
                MONDAY.plusDays(1),
                MONDAY.plusDays(1)
        );

        CalculateDailyAttendance calculator =
                new CalculateDailyAttendance(store());

        assertThatThrownBy(() -> calculator.execute(
                EMPLOYEE,
                MONDAY,
                schedule,
                assignment
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldIdentifyUnscheduledWork() {
        AttendanceSummary result = calculate(
                SUNDAY,
                officeSchedule(),
                event("2026-11-08T10:00:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-08T11:00:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.expectedMinutes()).isZero();
        assertThat(result.workedMinutes()).isEqualTo(60);
        assertThat(result.status())
                .isEqualTo(AttendanceStatus.UNSCHEDULED);
    }


    @Test
    void shouldNotCountWorkFromTheFollowingDay() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule(),
                event("2026-11-02T08:00:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-02T16:00:00Z", EMPLOYEE, TrackingType.Out),
                event("2026-11-03T08:00:00Z", EMPLOYEE, TrackingType.In),
                event("2026-11-03T16:00:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.workedMinutes()).isEqualTo(480);
        assertThat(result.expectedMinutes()).isEqualTo(480);
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void shouldMarkExitWithoutEntryAsIncomplete() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule(),
                event("2026-11-02T16:00:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.workedMinutes()).isZero();
        assertThat(result.status()).isEqualTo(AttendanceStatus.INCOMPLETE);
    }

    @Test
    void shouldIgnoreUnmatchedExitFromFollowingDay() {
        AttendanceSummary result = calculate(
                MONDAY,
                officeSchedule(),
                event("2026-11-03T06:00:00Z", EMPLOYEE, TrackingType.Out)
        );

        assertThat(result.workedMinutes()).isZero();
        assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
    }


    private AttendanceSummary calculate(
            LocalDate date,
            Schedule schedule,
            StoredTrackingEvent... events
    ) {
        ScheduleAssignment assignment = new ScheduleAssignment(
                EMPLOYEE,
                schedule.id(),
                date,
                date
        );

        return new CalculateDailyAttendance(
                store(events)
        ).execute(
                EMPLOYEE,
                date,
                schedule,
                assignment
        );
    }

    private Schedule officeSchedule() {
        return new ContinuousSchedule(
                "OFFICE-001",
                "Office",
                slot(8, 0, 16, 0),
                Set.of(DayOfWeek.MONDAY)
        );
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

    private static StoredTrackingEvent event(
            String timestamp,
            String employeeId,
            TrackingType type
    ) {
        return new StoredTrackingEvent(
                Instant.parse(timestamp),
                employeeId,
                "HOSPITAL",
                type
        );
    }

    private static EventStore store(
            StoredTrackingEvent... events
    ) {
        return new EventStore() {

            @Override
            public void register(TrackingEvent event) {
                throw new UnsupportedOperationException(
                        "Not needed for attendance calculation"
                );
            }

            @Override
            public List<StoredTrackingEvent> read(LocalDate date) {
                return Arrays.stream(events)
                        .filter(event ->
                                LocalDate.ofInstant(
                                        event.ts(),
                                        ZoneOffset.UTC
                                ).equals(date)
                        )
                        .toList();
            }
        };
    }
}