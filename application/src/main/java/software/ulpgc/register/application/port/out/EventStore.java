package software.ulpgc.register.application.port.out;

import software.ulpgc.register.domain.model.StoredTrackingEvent;
import software.ulpgc.register.domain.model.TrackingEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

public interface EventStore {
    void register(TrackingEvent event);
    List<StoredTrackingEvent> read(LocalDate day);

    default List<StoredTrackingEvent> read(LocalDate from, LocalDate through) {
        return LongStream.rangeClosed(from.toEpochDay(), through.toEpochDay())
                .mapToObj(LocalDate::ofEpochDay).flatMap(day -> read(day).stream()).toList();
    }
}