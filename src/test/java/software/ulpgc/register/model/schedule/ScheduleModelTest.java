package software.ulpgc.register.model.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleModelTest {

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 2);

    private static final LocalDate SUNDAY =
            LocalDate.of(2026, 11, 1);

    @Test
    void shouldCalculateContinuousSlotDuration() {
        TimeSlot slot = new TimeSlot(
                LocalTime.of(8, 0),
                LocalTime.of(16, 0)
        );

        assertThat(slot.minutes()).isEqualTo(480);
    }

    @Test
    void shouldCalculateOvernightSlotDuration() {
        TimeSlot slot = new TimeSlot(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0)
        );

        assertThat(slot.crossesMidnight()).isTrue();
        assertThat(slot.minutes()).isEqualTo(480);
    }

    @Test
    void shouldRejectEmptyTimeSlot() {
        assertThatThrownBy(() -> new TimeSlot(
                LocalTime.of(8, 0),
                LocalTime.of(8, 0)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnWorkingSlotOnMonday() {
        Schedule schedule = officeSchedule();

        assertThat(schedule.slotsOn(MONDAY)).hasSize(1);
        assertThat(schedule.expectedMinutesOn(MONDAY))
                .isEqualTo(480);
    }

    @Test
    void shouldReturnNoSlotsOnSunday() {
        Schedule schedule = officeSchedule();

        assertThat(schedule.slotsOn(SUNDAY)).isEmpty();
        assertThat(schedule.expectedMinutesOn(SUNDAY))
                .isZero();
    }

    @Test
    void shouldRejectOvernightContinuousSchedule() {
        TimeSlot night = new TimeSlot(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0)
        );

        assertThatThrownBy(() -> new ContinuousSchedule(
                "OFFICE-001",
                "Office",
                night,
                Set.of(DayOfWeek.MONDAY)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPreserveWorkingDaysAfterExternalModification() {
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY);

        Schedule schedule = new ContinuousSchedule(
                "OFFICE-001",
                "Office",
                new TimeSlot(
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0)
                ),
                days
        );

        days.clear();

        assertThat(schedule.slotsOn(MONDAY)).hasSize(1);
    }

    @Test
    void shouldCheckAssignmentPeriodInclusively() {
        ScheduleAssignment assignment = new ScheduleAssignment(
                "EMP-001",
                "OFFICE-001",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );

        assertThat(assignment.isActiveOn(
                LocalDate.of(2026, 11, 1)
        )).isTrue();

        assertThat(assignment.isActiveOn(
                LocalDate.of(2026, 11, 30)
        )).isTrue();

        assertThat(assignment.isActiveOn(
                LocalDate.of(2026, 12, 1)
        )).isFalse();
    }

    @Test
    void shouldRejectInvalidAssignmentPeriod() {
        assertThatThrownBy(() -> new ScheduleAssignment(
                "EMP-001",
                "OFFICE-001",
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 11, 1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Schedule officeSchedule() {
        return new ContinuousSchedule(
                "OFFICE-001",
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
}