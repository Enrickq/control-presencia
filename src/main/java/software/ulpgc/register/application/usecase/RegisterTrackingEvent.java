package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.EventStore;
import software.ulpgc.register.model.StoredTrackingEvent;
import software.ulpgc.register.model.TrackingEvent;
import software.ulpgc.register.model.TrackingType;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Optional;

public record RegisterTrackingEvent(EventStore eventStore) {

    public void execute(TrackingEvent event) {
        Optional<StoredTrackingEvent> previous = lastEventOf(event);
        validate(event, previous);
        eventStore.register(event);
    }

    private Optional<StoredTrackingEvent> lastEventOf(TrackingEvent event) {
        LocalDate day = LocalDate.ofInstant(event.ts(), ZoneOffset.UTC);

        return eventStore.read(day).stream()
                .filter(stored -> stored.employeeId().equals(event.employee().id()))
                .max(Comparator.comparing(StoredTrackingEvent::ts));
    }

    private void validate(TrackingEvent event, Optional<StoredTrackingEvent> previous) {
        if (previous.isPresent() && !event.ts().isAfter(previous.get().ts())) {
            throw new IllegalArgumentException("Tracking events must be chronological");
        }

        TrackingType lastType = previous
                .map(StoredTrackingEvent::type)
                .orElse(null);

        if (event.type() == TrackingType.In && lastType == TrackingType.In) {
            throw new IllegalStateException("Employee is already checked in");
        }

        if (event.type() == TrackingType.Out && lastType != TrackingType.In) {
            throw new IllegalStateException("Cannot check out without a previous entry");
        }
    }
}