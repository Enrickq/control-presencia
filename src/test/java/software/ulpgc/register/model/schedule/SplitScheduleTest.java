package software.ulpgc.register.model.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitScheduleTest {

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 2);

    private static final LocalDate SUNDAY =
            LocalDate.of(2026, 11, 1);

    @Test
    void shouldReturnSlotsOnWorkingDay() {
        Schedule schedule = restaurantSchedule();

        assertThat(schedule.slotsOn(MONDAY))
                .containsExactly(morning(), evening());
    }

    @Test
    void shouldCalculateExpectedWorkingMinutes() {
        Schedule schedule = restaurantSchedule();

        assertThat(schedule.expectedMinutesOn(MONDAY))
                .isEqualTo(480);
    }

    @Test
    void shouldReturnNoSlotsOnNonWorkingDay() {
        Schedule schedule = restaurantSchedule();

        assertThat(schedule.slotsOn(SUNDAY)).isEmpty();
        assertThat(schedule.expectedMinutesOn(SUNDAY)).isZero();
    }

    @Test
    void shouldSortUnorderedSlots() {
        Schedule schedule = new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(evening(), morning()),
                Set.of(DayOfWeek.MONDAY)
        );

        assertThat(schedule.slotsOn(MONDAY))
                .containsExactly(morning(), evening());
    }

    @Test
    void shouldRejectOverlappingSlots() {
        TimeSlot first = new TimeSlot(
                LocalTime.of(10, 0),
                LocalTime.of(14, 0)
        );

        TimeSlot second = new TimeSlot(
                LocalTime.of(13, 0),
                LocalTime.of(18, 0)
        );

        assertThatThrownBy(() -> new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(first, second),
                Set.of(DayOfWeek.MONDAY)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectTouchingSlots() {
        TimeSlot first = new TimeSlot(
                LocalTime.of(10, 0),
                LocalTime.of(14, 0)
        );

        TimeSlot second = new TimeSlot(
                LocalTime.of(14, 0),
                LocalTime.of(18, 0)
        );

        assertThatThrownBy(() -> new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(first, second),
                Set.of(DayOfWeek.MONDAY)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSingleSlot() {
        assertThatThrownBy(() -> new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(morning()),
                Set.of(DayOfWeek.MONDAY)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectOvernightSlots() {
        TimeSlot overnight = new TimeSlot(
                LocalTime.of(22, 0),
                LocalTime.of(6, 0)
        );

        assertThatThrownBy(() -> new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(morning(), overnight),
                Set.of(DayOfWeek.MONDAY)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldProtectSlotsFromExternalModification() {
        List<TimeSlot> slots = new ArrayList<>(
                List.of(morning(), evening())
        );

        Schedule schedule = new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                slots,
                Set.of(DayOfWeek.MONDAY)
        );

        slots.clear();

        assertThat(schedule.slotsOn(MONDAY))
                .containsExactly(morning(), evening());
    }

    @Test
    void shouldProtectWorkingDaysFromExternalModification() {
        Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY);

        Schedule schedule = new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(morning(), evening()),
                days
        );

        days.clear();

        assertThat(schedule.slotsOn(MONDAY))
                .containsExactly(morning(), evening());
    }

    @Test
    void shouldRespectScheduleContract() {
        Schedule continuous = new ContinuousSchedule(
                "OFFICE-001",
                "Office",
                new TimeSlot(
                        LocalTime.of(8, 0),
                        LocalTime.of(16, 0)
                ),
                Set.of(DayOfWeek.MONDAY)
        );

        Schedule split = restaurantSchedule();

        assertThat(continuous.expectedMinutesOn(MONDAY))
                .isEqualTo(split.expectedMinutesOn(MONDAY));
    }

    private Schedule restaurantSchedule() {
        return new SplitSchedule(
                "RESTAURANT-001",
                "Restaurant",
                List.of(morning(), evening()),
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                )
        );
    }

    private TimeSlot morning() {
        return new TimeSlot(
                LocalTime.of(10, 0),
                LocalTime.of(14, 0)
        );
    }

    private TimeSlot evening() {
        return new TimeSlot(
                LocalTime.of(18, 0),
                LocalTime.of(22, 0)
        );
    }
}