package software.ulpgc.register.model.schedule;

import java.time.LocalDate;
import java.util.List;

public interface Schedule {

    String id();

    String name();

    List<TimeSlot> slotsOn(LocalDate date);

    default long expectedMinutesOn(LocalDate date) {
        return slotsOn(date).stream()
                .mapToLong(TimeSlot::minutes)
                .sum();
    }
}