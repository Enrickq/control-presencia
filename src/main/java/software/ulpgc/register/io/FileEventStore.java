package software.ulpgc.register.io;

import software.ulpgc.register.model.TrackingEvent;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public record FileEventStore(File root) implements EventStore {

    @Override
    public void register(TrackingEvent event) {
        LocalDate day = LocalDate.ofInstant(event.ts(), ZoneOffset.UTC);
        Path file = root.toPath()
                .resolve(Integer.toString(day.getYear()))
                .resolve(day.format(DateTimeFormatter.BASIC_ISO_DATE) + ".log");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line(event), StandardCharsets.UTF_8, CREATE, APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist tracking event", e);
        }
    }

    private static String line(TrackingEvent event) {
        return "%s\t%s\t%s\t%s\n".formatted(
                event.ts(), event.employee().id(), event.place().id(), event.type());
    }
}
