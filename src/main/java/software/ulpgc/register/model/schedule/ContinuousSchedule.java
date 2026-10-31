package software.ulpgc.register.model.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ContinuousSchedule(
        String id,
        String name,
        TimeSlot slot,
        Set<DayOfWeek> workingDays
) implements Schedule {

    public ContinuousSchedule {
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

        Objects.requireNonNull(slot);
        Objects.requireNonNull(workingDays);

        if (workingDays.isEmpty()) {
            throw new IllegalArgumentException(
                    "Working days cannot be empty"
            );
        }

        if (slot.crossesMidnight()) {
            throw new IllegalArgumentException(
                    "Continuous schedules cannot cross midnight"
            );
        }

        workingDays = Set.copyOf(workingDays);
    }

    @Override
    public List<TimeSlot> slotsOn(LocalDate date) {
        Objects.requireNonNull(date);

        return workingDays.contains(date.getDayOfWeek())
                ? List.of(slot)
                : List.of();
    }
}