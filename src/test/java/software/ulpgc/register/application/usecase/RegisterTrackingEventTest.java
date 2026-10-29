package software.ulpgc.register.application.usecase;

import org.junit.jupiter.api.BeforeEach;
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

class RegisterTrackingEventTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);

    @TempDir
    Path tempDir;

    private EventStore eventStore;
    private RegisterTrackingEvent registrar;

    private final Employee employee =
            new Employee("EMP-001", "Example Employee");

    private final Place place =
            new Place("OFFICE", "Main Office");

    @BeforeEach
    void setUp() {
        eventStore = new FileEventStore(tempDir.toFile());
        registrar = new RegisterTrackingEvent(eventStore);
    }

    @Test
    void shouldRegisterFirstEntry() {
        registrar.execute(event("08:00", employee, TrackingType.In));

        assertThat(eventStore.read(DAY))
                .extracting(StoredTrackingEvent::type)
                .containsExactly(TrackingType.In);
    }

    @Test
    void shouldRegisterExitAfterEntry() {
        registrar.execute(event("08:00", employee, TrackingType.In));
        registrar.execute(event("16:00", employee, TrackingType.Out));

        assertThat(eventStore.read(DAY))
                .extracting(StoredTrackingEvent::type)
                .containsExactly(TrackingType.In, TrackingType.Out);
    }

    @Test
    void shouldRejectDuplicateEntry() {
        registrar.execute(event("08:00", employee, TrackingType.In));

        assertThatThrownBy(() ->
                registrar.execute(event("09:00", employee, TrackingType.In)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(eventStore.read(DAY)).hasSize(1);
    }

    @Test
    void shouldRejectExitWithoutEntry() {
        assertThatThrownBy(() ->
                registrar.execute(event("16:00", employee, TrackingType.Out)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(eventStore.read(DAY)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateExit() {
        registrar.execute(event("08:00", employee, TrackingType.In));
        registrar.execute(event("16:00", employee, TrackingType.Out));

        assertThatThrownBy(() ->
                registrar.execute(event("17:00", employee, TrackingType.Out)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAllowSecondEntryAfterExit() {
        registrar.execute(event("08:00", employee, TrackingType.In));
        registrar.execute(event("12:00", employee, TrackingType.Out));
        registrar.execute(event("13:00", employee, TrackingType.In));
        registrar.execute(event("17:00", employee, TrackingType.Out));

        assertThat(eventStore.read(DAY))
                .extracting(StoredTrackingEvent::type)
                .containsExactly(
                        TrackingType.In,
                        TrackingType.Out,
                        TrackingType.In,
                        TrackingType.Out
                );
    }

    @Test
    void shouldTrackEmployeesIndependently() {
        Employee other = new Employee("EMP-002", "Another Employee");

        registrar.execute(event("08:00", employee, TrackingType.In));
        registrar.execute(event("08:05", other, TrackingType.In));

        assertThat(eventStore.read(DAY))
                .extracting(StoredTrackingEvent::employeeId)
                .containsExactly("EMP-001", "EMP-002");
    }

    @Test
    void shouldRejectEarlierTimestamp() {
        registrar.execute(event("09:00", employee, TrackingType.In));

        assertThatThrownBy(() ->
                registrar.execute(event("08:00", employee, TrackingType.Out)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(eventStore.read(DAY)).hasSize(1);
    }

    private TrackingEvent event(
            String time,
            Employee employee,
            TrackingType type
    ) {
        return new TrackingEvent(
                Instant.parse("2026-10-29T" + time + ":00Z"),
                employee,
                place,
                type
        );
    }
}