package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingEvent;

import java.time.LocalDate;
import java.util.List;

public interface EventStore {
    void register(TrackingEvent event);
    List<StoredTrackingEvent> read(LocalDate day);
}