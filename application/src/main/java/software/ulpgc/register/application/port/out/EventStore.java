package software.ulpgc.register.application.port.out;

import software.ulpgc.register.domain.model.StoredTrackingEvent;
import software.ulpgc.register.domain.model.TrackingEvent;

import java.time.LocalDate;
import java.util.List;

public interface EventStore {
    void register(TrackingEvent event);
    List<StoredTrackingEvent> read(LocalDate day);
}