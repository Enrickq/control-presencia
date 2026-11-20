package software.ulpgc.register.query.adapter.http;

import io.javalin.Javalin;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.domain.model.*;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileEventStore;
import software.ulpgc.register.infrastructure.configuration.ServerMain;
import software.ulpgc.register.query.model.DayQuery;
import software.ulpgc.register.query.model.Punch;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AttendanceHttpIntegrationTest {
    @TempDir Path dir;
    private Javalin server;
    private LocalConnector connector;
    private HttpAttendanceGateway gateway;
    private final DayQuery query = DayQuery.parse("DEMO", "2026-11-20");

    @BeforeEach void start() {
        server = ServerMain.createServer(dir);
        var jetty = server.jettyServer().server();
        connector = new LocalConnector(jetty);
        jetty.addConnector(connector);
        server.start();
        gateway = new HttpAttendanceGateway(URI.create("http://localhost"), request ->
                CompletableFuture.supplyAsync(() -> exchange("GET", request.uri().getRawPath() + "?"
                        + request.uri().getRawQuery(), "")));
    }

    @AfterEach void stop() {
        if (server != null) server.stop();
    }

    @Test void combinesRealIndicatorsAndPunchesWithTheirOriginalTimes() {
        configureDay();
        punch("2026-11-20T08:10:00Z", TrackingType.In);
        punch("2026-11-20T15:55:00Z", TrackingType.Out);
        var daily = gateway.attendance(query).orTimeout(5, TimeUnit.SECONDS).join();
        assertThat(daily.expectedMinutes()).isEqualTo(480);
        assertThat(daily.workedMinutes()).isEqualTo(465);
        assertThat(daily.lateMinutes()).isEqualTo(10);
        assertThat(daily.balanceMinutes()).isEqualTo(-15);
        assertThat(daily.status()).isEqualTo("PRESENT");
        assertThat(daily.incidents().orElseThrow()).containsExactly("LATE_ARRIVAL");
        assertThat(gateway.punches(query).join()).containsExactly(
                new Punch(Instant.parse("2026-11-20T08:10:00Z"), "SALA", Punch.Type.IN),
                new Punch(Instant.parse("2026-11-20T15:55:00Z"), "SALA", Punch.Type.OUT));
    }

    @Test void distinguishesAbsenceFromAnIncompletePunchSequence() {
        configureDay();
        assertThat(gateway.punches(query).join()).isEmpty();
        assertThat(gateway.attendance(query).join().incidents().orElseThrow()).containsExactly("ABSENCE");
        punch("2026-11-20T08:00:00Z", TrackingType.In);
        assertThat(gateway.attendance(query).join().incidents().orElseThrow()).containsExactly("INCOMPLETE_PUNCHES");
    }

    @Test void preservesCalendarDayScopeForOvernightWork() {
        configureDay();
        punch("2026-11-20T22:00:00Z", TrackingType.In);
        punch("2026-11-21T06:00:00Z", TrackingType.Out);
        assertThat(gateway.attendance(query).join().workedMinutes()).isEqualTo(480);
        assertThat(gateway.punches(query).join()).extracting(Punch::type).containsExactly(Punch.Type.IN);
    }

    @Test void translatesBackendErrorsAndAllowsPunchesWithoutAnActiveAssignment() {
        assertThatThrownBy(() -> gateway.attendance(query).join()).cause().hasMessageContaining("no está registrado");
        employee();
        punch("2026-11-20T08:00:00Z", TrackingType.In);
        assertThatThrownBy(() -> gateway.attendance(query).join()).cause().hasMessageContaining("no tiene horario");
        assertThat(gateway.punches(query).join()).hasSize(1);
        var invalid = exchange("GET", "/api/v1/attendance?employeeId=DEMO&date=2026-02-30", "");
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body()).contains("INVALID_DATE");
    }

    private void configureDay() {
        employee();
        post("schedules", """
            {"type":"CONTINUOUS","id":"DIA","name":"Diurno","workingDays":["FRIDAY"],"slot":{"start":"08:00","end":"16:00"}}""");
        post("assignments", """
            {"employeeId":"DEMO","scheduleId":"DIA","validFrom":"2026-11-01","validThrough":"2026-11-30"}""");
    }

    private void employee() {
        post("employees", """
            {"id":"DEMO","name":"Demostración"}""");
    }

    private void punch(String time, TrackingType type) {
        new FileEventStore(dir.toFile()).register(new TrackingEvent(Instant.parse(time),
                new Employee("DEMO", "Demostración"), new Place("SALA", "Sala de prueba"), type));
    }

    private void post(String resource, String body) {
        assertThat(exchange("POST", "/api/v1/" + resource, body).status()).isEqualTo(201);
    }

    private HttpAttendanceGateway.Reply exchange(String method, String target, String body) {
        try {
            String raw = method + " " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                    + "Content-Type: application/json\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                    + "\r\n\r\n" + body;
            ByteBuffer response = connector.getResponse(StandardCharsets.UTF_8.encode(raw), 5, TimeUnit.SECONDS);
            String wire = StandardCharsets.UTF_8.decode(response).toString();
            int status = Integer.parseInt(wire.split(" ", 3)[1]);
            return new HttpAttendanceGateway.Reply(status, wire.substring(wire.indexOf("\r\n\r\n") + 4));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
