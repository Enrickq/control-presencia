package software.ulpgc.register.model.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ShiftSchedule(
        String id,
        String name,
        Map<DayOfWeek, TimeSlot> weeklySlots
) implements Schedule {

    public ShiftSchedule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Schedule id is required"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Schedule name is required"
            );
        }

        Objects.requireNonNull(weeklySlots);

        if (weeklySlots.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one shift is required"
            );
        }

        weeklySlots = Map.copyOf(weeklySlots);
    }

    @Override
    public List<TimeSlot> slotsOn(LocalDate date) {
        Objects.requireNonNull(date);

        TimeSlot slot = weeklySlots.get(date.getDayOfWeek());

        return slot == null
                ? List.of()
                : List.of(slot);
    }
}