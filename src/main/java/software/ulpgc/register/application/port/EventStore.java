package software.ulpgc.register.application.port;

import software.ulpgc.register.model.StoredTrackingEvent;
import software.ulpgc.register.model.TrackingEvent;

import java.time.LocalDate;
import java.util.List;

public interface EventStore {
    void register(TrackingEvent event);
    List<StoredTrackingEvent> read(LocalDate day);
}