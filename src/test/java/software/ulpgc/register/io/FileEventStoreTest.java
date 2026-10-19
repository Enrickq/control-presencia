package software.ulpgc.register.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.model.Employee;
import software.ulpgc.register.model.Place;
import software.ulpgc.register.model.TrackingEvent;
import software.ulpgc.register.model.TrackingType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FileEventStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldPersistAnEntryAndExitWithoutOverwriting() throws IOException {
        EventStore store = new FileEventStore(tempDir.toFile());
        Employee employee = new Employee("EMP-001", "Example Employee");
        Place place = new Place("OFFICE", "Main Office");

        store.register(new TrackingEvent(
                Instant.parse("2026-10-19T08:00:00Z"), employee, place, TrackingType.In));
        store.register(new TrackingEvent(
                Instant.parse("2026-10-19T16:00:00Z"), employee, place, TrackingType.Out));

        Path log = tempDir.resolve("2026").resolve("20261019.log");
        assertThat(log).exists();
        assertThat(Files.readAllLines(log)).containsExactly(
                "2026-10-19T08:00:00Z\tEMP-001\tOFFICE\tIn",
                "2026-10-19T16:00:00Z\tEMP-001\tOFFICE\tOut");
    }

    @Test
    void shouldGroupEventsByUtcDate() {
        EventStore store = new FileEventStore(tempDir.toFile());
        Employee employee = new Employee("EMP-002", "Example Employee 2");
        Place place = new Place("HOSPITAL", "Hospital");

        store.register(new TrackingEvent(
                Instant.parse("2026-10-20T00:01:00Z"), employee, place, TrackingType.In));

        assertThat(tempDir.resolve("2026/20261020.log")).exists();
    }
}
