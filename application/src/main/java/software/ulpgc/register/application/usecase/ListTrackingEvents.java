
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.TrackingEventsQuery;
import software.ulpgc.register.application.port.out.EventStore;
import software.ulpgc.register.domain.model.StoredTrackingEvent;

import java.time.LocalDate;
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
        Objects.requireNonNull(date);

        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        return eventStore.read(date).stream()
                .filter(event ->
                        event.employeeId().equals(employeeId))
                .toList();
    }
}
