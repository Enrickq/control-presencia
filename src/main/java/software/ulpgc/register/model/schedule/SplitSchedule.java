package software.ulpgc.register.model.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public record SplitSchedule(
        String id,
        String name,
        List<TimeSlot> slots,
        Set<DayOfWeek> workingDays
) implements Schedule {

    public SplitSchedule {
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

        Objects.requireNonNull(slots);
        Objects.requireNonNull(workingDays);

        if (slots.size() < 2) {
            throw new IllegalArgumentException(
                    "Split schedules require at least two slots"
            );
        }

        if (workingDays.isEmpty()) {
            throw new IllegalArgumentException(
                    "Working days cannot be empty"
            );
        }

        if (slots.stream().anyMatch(
                slot -> slot == null || slot.crossesMidnight()
        )) {
            throw new IllegalArgumentException(
                    "Split schedule slots must be within one day"
            );
        }

        List<TimeSlot> ordered = slots.stream()
                .sorted(Comparator.comparing(TimeSlot::start))
                .toList();

        boolean invalidBreaks = IntStream.range(1, ordered.size())
                .anyMatch(i -> !ordered.get(i - 1).end()
                        .isBefore(ordered.get(i).start()));

        if (invalidBreaks) {
            throw new IllegalArgumentException(
                    "Split schedule slots must not overlap or touch"
            );
        }

        slots = List.copyOf(ordered);
        workingDays = Set.copyOf(workingDays);
    }

    @Override
    public List<TimeSlot> slotsOn(LocalDate date) {
        Objects.requireNonNull(date);

        return workingDays.contains(date.getDayOfWeek())
                ? slots
                : List.of();
    }
}