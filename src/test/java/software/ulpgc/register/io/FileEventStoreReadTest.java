package software.ulpgc.register.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.application.port.EventStore;
import software.ulpgc.register.model.*;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FileEventStoreReadTest {

    @TempDir
    Path tempDir;

    private final Employee employee =
            new Employee("EMP-001", "Example Employee");

    private final Place place =
            new Place("OFFICE", "Main Office");

    @Test
    void shouldReadSavedEventsInOrder() {
        EventStore store = new FileEventStore(tempDir.toFile());

        store.register(event("2026-10-21T08:00:00Z", TrackingType.In));
        store.register(event("2026-10-21T16:00:00Z", TrackingType.Out));

        assertThat(store.read(LocalDate.of(2026, 10, 21)))
                .containsExactly(
                        stored("2026-10-21T08:00:00Z", TrackingType.In),
                        stored("2026-10-21T16:00:00Z", TrackingType.Out)
                );
    }

    @Test
    void shouldReturnEmptyListWhenFileDoesNotExist() {
        EventStore store = new FileEventStore(tempDir.toFile());

        assertThat(store.read(LocalDate.of(2026, 10, 21)))
                .isEmpty();
    }

    @Test
    void shouldReadEventsFromNewStoreInstance() {
        EventStore writer = new FileEventStore(tempDir.toFile());

        writer.register(event("2026-10-21T08:00:00Z", TrackingType.In));

        EventStore reader = new FileEventStore(tempDir.toFile());

        assertThat(reader.read(LocalDate.of(2026, 10, 21)))
                .containsExactly(
                        stored("2026-10-21T08:00:00Z", TrackingType.In)
                );
    }

    @Test
    void shouldReadOnlyRequestedDay() {
        EventStore store = new FileEventStore(tempDir.toFile());

        store.register(event("2026-10-21T08:00:00Z", TrackingType.In));
        store.register(event("2026-10-22T08:00:00Z", TrackingType.In));

        assertThat(store.read(LocalDate.of(2026, 10, 21)))
                .containsExactly(
                        stored("2026-10-21T08:00:00Z", TrackingType.In)
                );
    }

    private TrackingEvent event(String timestamp, TrackingType type) {
        return new TrackingEvent(
                Instant.parse(timestamp), employee, place, type
        );
    }

    private StoredTrackingEvent stored(String timestamp, TrackingType type) {
        return new StoredTrackingEvent(
                Instant.parse(timestamp), employee.id(), place.id(), type
        );
    }
}