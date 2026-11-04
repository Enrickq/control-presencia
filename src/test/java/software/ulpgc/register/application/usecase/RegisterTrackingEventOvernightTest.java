package software.ulpgc.register.application.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.application.port.EventStore;
import software.ulpgc.register.io.FileEventStore;
import software.ulpgc.register.model.Employee;
import software.ulpgc.register.model.Place;
import software.ulpgc.register.model.StoredTrackingEvent;
import software.ulpgc.register.model.TrackingEvent;
import software.ulpgc.register.model.TrackingType;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterTrackingEventOvernightTest {

    @TempDir
    Path tempDir;

    private final Employee employee =
            new Employee("EMP-001", "Example Employee");

    private final Place place =
            new Place("HOSPITAL", "Hospital");

    @Test
    void shouldAllowExitOnFollowingDay() {
        EventStore store = new FileEventStore(tempDir.toFile());
        RegisterTrackingEvent registrar =
                new RegisterTrackingEvent(store);

        registrar.execute(event(
                "2026-11-04T22:00:00Z",
                TrackingType.In
        ));

        registrar.execute(event(
                "2026-11-05T06:00:00Z",
                TrackingType.Out
        ));

        assertThat(store.read(LocalDate.of(2026, 11, 5)))
                .extracting(StoredTrackingEvent::type)
                .containsExactly(TrackingType.Out);
    }

    @Test
    void shouldRejectDuplicateEntryAfterMidnight() {
        EventStore store = new FileEventStore(tempDir.toFile());
        RegisterTrackingEvent registrar =
                new RegisterTrackingEvent(store);

        registrar.execute(event(
                "2026-11-04T22:00:00Z",
                TrackingType.In
        ));

        assertThatThrownBy(() -> registrar.execute(event(
                "2026-11-05T01:00:00Z",
                TrackingType.In
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(store.read(LocalDate.of(2026, 11, 5)))
                .isEmpty();
    }

    @Test
    void shouldHandleYearBoundary() {
        EventStore store = new FileEventStore(tempDir.toFile());
        RegisterTrackingEvent registrar =
                new RegisterTrackingEvent(store);

        registrar.execute(event(
                "2026-12-31T22:00:00Z",
                TrackingType.In
        ));

        registrar.execute(event(
                "2027-01-01T06:00:00Z",
                TrackingType.Out
        ));

        assertThat(store.read(LocalDate.of(2027, 1, 1)))
                .extracting(StoredTrackingEvent::type)
                .containsExactly(TrackingType.Out);
    }

    private TrackingEvent event(String timestamp, TrackingType type) {
        return new TrackingEvent(
                Instant.parse(timestamp),
                employee,
                place,
                type
        );
    }
}