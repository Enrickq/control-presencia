package software.ulpgc.register.query.adapter.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.infrastructure.configuration.ServerMain;
import software.ulpgc.register.query.model.DayQuery;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class QueryLoopbackTest {
    @Test void readsBothEndpointsThroughTheProductionHttpClient(@TempDir Path dir) throws Exception {
        var server = ServerMain.createServer(dir).start("127.0.0.1", 0);
        try (var client = HttpClient.newHttpClient()) {
            var gateway = new HttpAttendanceGateway(URI.create("http://127.0.0.1:" + server.port()), client);
            var query = DayQuery.parse("EJEMPLO", "2026-11-20");
            assertThat(gateway.punches(query).get(5, TimeUnit.SECONDS)).isEmpty();
            assertThatThrownBy(() -> gateway.attendance(query).get(5, TimeUnit.SECONDS))
                    .cause().hasMessageContaining("no está registrado");
        } finally {
            server.stop();
        }
    }
}
