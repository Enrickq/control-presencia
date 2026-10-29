package software.ulpgc.register.io;

import software.ulpgc.register.application.port.EventStore;
import software.ulpgc.register.model.StoredTrackingEvent;
import software.ulpgc.register.model.TrackingEvent;
import software.ulpgc.register.model.TrackingType;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public record FileEventStore(File root) implements EventStore {

    @Override
    public void register(TrackingEvent event) {
        Path file = fileFor(LocalDate.ofInstant(event.ts(), ZoneOffset.UTC));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line(event), StandardCharsets.UTF_8, CREATE, APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist tracking event", e);
        }
    }

    @Override
    public List<StoredTrackingEvent> read(LocalDate day) {
        Path file = fileFor(day);
        if (Files.notExists(file)) return List.of();

        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(FileEventStore::parse).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read tracking events", e);
        }
    }

    private Path fileFor(LocalDate day) {
        return root.toPath()
                .resolve(Integer.toString(day.getYear()))
                .resolve(day.format(DateTimeFormatter.BASIC_ISO_DATE) + ".log");
    }

    private static StoredTrackingEvent parse(String line) {
        String[] fields = line.split("\t", -1);

        if (fields.length != 4) {
            throw new IllegalArgumentException("Invalid tracking event line: " + line);
        }

        return new StoredTrackingEvent(
                Instant.parse(fields[0]),
                fields[1],
                fields[2],
                TrackingType.valueOf(fields[3])
        );
    }

    private static String line(TrackingEvent event) {
        return "%s\t%s\t%s\t%s\n".formatted(
                event.ts(),
                event.employee().id(),
                event.place().id(),
                event.type()
        );
    }
}