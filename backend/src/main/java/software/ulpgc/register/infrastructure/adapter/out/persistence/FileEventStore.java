package software.ulpgc.register.infrastructure.adapter.out.persistence;

import software.ulpgc.register.application.port.out.EventStore;
import software.ulpgc.register.domain.model.StoredTrackingEvent;
import software.ulpgc.register.domain.model.TrackingEvent;
import software.ulpgc.register.domain.model.TrackingType;

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
import java.time.format.DateTimeParseException;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public record FileEventStore(File root) implements EventStore {

    @Override
    public synchronized void register(TrackingEvent event) {
        Path file = fileFor(LocalDate.ofInstant(event.ts(), ZoneOffset.UTC));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line(event), StandardCharsets.UTF_8, CREATE, APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist tracking event", e);
        }
    }

    @Override
    public synchronized List<StoredTrackingEvent> read(LocalDate day) {
        Path file = fileFor(day);
        if (Files.notExists(file)) return List.of();

        return readFile(file);
    }

    @Override
    public synchronized List<StoredTrackingEvent> read(LocalDate from, LocalDate through) {
        Path base = root.toPath();
        if (Files.notExists(base)) return List.of();
        try (var files = Files.walk(base, 2)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> isEventFileInRange(file, from, through))
                    .flatMap(file -> readFile(file).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list tracking event files", e);
        }
    }

    private boolean isEventFileInRange(Path path, LocalDate from, LocalDate through) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".log")) return false;
        try {
            LocalDate day = LocalDate.parse(name.substring(0, name.length() - 4), DateTimeFormatter.BASIC_ISO_DATE);
            return path.equals(fileFor(day)) && !day.isBefore(from) && !day.isAfter(through);
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static List<StoredTrackingEvent> readFile(Path file) {
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