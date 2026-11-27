
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.TrackingEventsQuery;
import software.ulpgc.register.application.port.out.EventStore;
import software.ulpgc.register.domain.model.StoredTrackingEvent;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ListTrackingEvents(EventStore eventStore)
        implements TrackingEventsQuery {

    public ListTrackingEvents {
        Objects.requireNonNull(eventStore);
    }

    @Override
    public List<StoredTrackingEvent> execute(
            LocalDate date,
            String employeeId
    ) {
        return execute(date, date, employeeId);
    }

    @Override
    public List<StoredTrackingEvent> execute(LocalDate from, LocalDate through, String employeeId) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(through);
        if (through.isBefore(from)) throw new IllegalArgumentException("End date precedes start date");

        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        var events = from.equals(through) ? eventStore.read(from) : eventStore.read(from, through);
        return events.stream()
                .filter(event ->
                        event.employeeId().equals(employeeId))
                .sorted(Comparator.comparing(StoredTrackingEvent::ts))
                .toList();
    }
}
