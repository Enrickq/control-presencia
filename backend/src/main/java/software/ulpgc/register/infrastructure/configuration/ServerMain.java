
package software.ulpgc.register.infrastructure.configuration;

import io.javalin.Javalin;
import software.ulpgc.register.application.usecase.ListTrackingEvents;
import software.ulpgc.register.application.usecase.RegisterTrackingEvent;
import software.ulpgc.register.infrastructure.adapter.in.http.ApiServer;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileEventStore;

import java.nio.file.Path;

public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(
                System.getenv().getOrDefault(
                        "PORT", "7070"
                )
        );

        Path dataDirectory = Path.of(
                System.getenv().getOrDefault(
                        "DATA_DIR", "data/events"
                )
        );

        FileEventStore store =
                new FileEventStore(dataDirectory.toFile());

        Javalin server = ApiServer.create(
                new RegisterTrackingEvent(store),
                new ListTrackingEvents(store)
        );

        server.start("0.0.0.0", port);
    }
}
