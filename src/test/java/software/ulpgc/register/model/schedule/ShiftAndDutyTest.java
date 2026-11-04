package software.ulpgc.register.model.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiftAndDutyTest {

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 2);

    private static final LocalDate TUESDAY =
            LocalDate.of(2026, 11, 3);

    private static final LocalDate WEDNESDAY =
            LocalDate.of(2026, 11, 4);

    @Test
    void shouldCalculateOvernightShiftDuration() {
        Schedule schedule = hospitalSchedule();

        assertThat(schedule.expectedMinutesOn(TUESDAY))
                .isEqualTo(480);
    }

    @Test
    void shouldAssignNightShiftToStartingDay() {
        Schedule schedule = new ShiftSchedule(
                "NIGHT-001",
                "Night shift",
                Map.of(DayOfWeek.TUESDAY, nightShift())
        );

        assertThat(schedule.slotsOn(TUESDAY))
                .containsExactly(nightShift());

        assertThat(schedule.slotsOn(WEDNESDAY))
                .isEmpty();
    }

    @Test
    void shouldSupportDifferentShiftsByWeekday() {
        Schedule schedule = hospitalSchedule();

        assertThat(schedule.slotsOn(MONDAY))
                .containsExactly(morningShift());

        assertThat(schedule.slotsOn(TUESDAY))
                .containsExactly(nightShift());
    }

    @Test
    void shouldReturnEmptyListWhenNoShiftIsAssigned() {
        Schedule schedule = hospitalSchedule();

        assertThat(schedule.slotsOn(WEDNESDAY))
                .isEmpty();

        assertThat(schedule.expectedMinutesOn(WEDNESDAY))
                .isZero();
    }

    @Test
    void shouldProtectWeeklySlotsFromExternalChanges() {
        Map<DayOfWeek, TimeSlot> slots =
                new EnumMap<>(DayOfWeek.class);

        slots.put(DayOfWeek.TUESDAY, nightShift());

        Schedule schedule = new ShiftSchedule(
                "NIGHT-001",
                "Night shift",
                slots
        );

        slots.clear();

        assertThat(schedule.slotsOn(TUESDAY))
                .containsExactly(nightShift());
    }

    @Test
    void shouldRejectEmptyShiftDefinition() {
        assertThatThrownBy(() -> new ShiftSchedule(
                "HOSPITAL-001",
                "Hospital",
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankShiftId() {
        assertThatThrownBy(() -> new ShiftSchedule(
                " ",
                "Hospital",
                Map.of(DayOfWeek.TUESDAY, nightShift())
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldModelOnSiteDuty() {
        Duty duty = new Duty(
                "EMP-003",
                WEDNESDAY,
                dutySlot(),
                DutyMode.ON_SITE
        );

        assertThat(duty.expectedPresenceMinutes())
                .isEqualTo(720);

        assertThat(duty.endDay())
                .isEqualTo(WEDNESDAY.plusDays(1));
    }

    @Test
    void shouldNotCountOnCallAvailabilityAsPresence() {
        Duty duty = new Duty(
                "EMP-003",
                WEDNESDAY,
                dutySlot(),
                DutyMode.ON_CALL
        );

        assertThat(duty.slot().minutes())
                .isEqualTo(720);

        assertThat(duty.expectedPresenceMinutes())
                .isZero();
    }

    @Test
    void shouldRejectDutyWithoutEmployee() {
        assertThatThrownBy(() -> new Duty(
                "",
                WEDNESDAY,
                dutySlot(),
                DutyMode.ON_SITE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Schedule hospitalSchedule() {
        return new ShiftSchedule(
                "HOSPITAL-001",
                "Hospital shifts",
                Map.of(
                        DayOfWeek.MONDAY, morningShift(),
                        DayOfWeek.TUESDAY, nightShift()
                )
        );
    }

    private TimeSlot morningShift() {
        return new TimeSlot(
                LocalTime.of(6, 0),
                LocalTime.of(14, 0)
        );
    }

    private TimeSlot nightShift() {
        return new TimeSlot(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0)
        );
    }

    private TimeSlot dutySlot() {
        return new TimeSlot(
                LocalTime.of(20, 0),
                LocalTime.of(8, 0)
        );
    }
}