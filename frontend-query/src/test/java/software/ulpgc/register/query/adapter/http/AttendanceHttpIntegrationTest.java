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

    @Test void rejectsAnUnknownEmployeeBeforeWritingAndAcceptsTheSamePunchAfterRegistration() {
        String entry = """
                {"employeeId":"DEMO","employeeName":"Persona de prueba","placeId":"SALA","placeName":"Sala","type":"IN"}""";
        var rejected = exchange("POST", "/api/v1/punches", entry);
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.body()).contains("Employee does not exist");
        employee();
        assertThat(exchange("POST", "/api/v1/punches", entry).status()).isEqualTo(201);
        assertThat(exchange("POST", "/api/v1/punches", entry).status()).isEqualTo(409);
    }

    @Test void periodReadsBothInclusiveDatesAcrossYearsAndPreservesHistoricalPunches() {
        punch("2026-12-30T23:59:59Z", TrackingType.Out);
        punch("2026-12-31T22:00:00Z", TrackingType.In);
        punch("2027-01-01T06:00:00Z", TrackingType.Out);
        punch("2027-01-02T00:00:00Z", TrackingType.In);
        new FileEventStore(dir.toFile()).register(new TrackingEvent(Instant.parse("2026-12-31T23:00:00Z"),
                new Employee("OTRA", "Otra persona"), new Place("SALA", "Sala"), TrackingType.In));
        var first = DayQuery.parse("DEMO", "2026-12-31");
        assertThat(gateway.punches(first, first.date().plusDays(1)).join()).extracting(Punch::timestamp)
                .containsExactly(Instant.parse("2026-12-31T22:00:00Z"), Instant.parse("2027-01-01T06:00:00Z"));
        assertThat(gateway.punches(first).join()).hasSize(1);
    }

    @Test void rejectsMixedIncompleteAndReversedPeriodParameters() {
        String url = "/api/v1/punches?employeeId=DEMO&";
        for (String dates : java.util.List.of("from=2026-11-25", "to=2026-11-25",
                "date=2026-11-25&to=2026-11-26", "from=2026-11-26&to=2026-11-25",
                "from=2026-02-30&to=2026-11-26"))
            assertThat(exchange("GET", url + dates, "").status()).as(dates).isEqualTo(400);
    }

    @Test void retainsScheduleChangesAndReproducesTheEarlierIncidentAfterRestart() {
        configureDay();
        punch("2026-11-20T08:10:00Z", TrackingType.In);
        punch("2026-11-20T15:55:00Z", TrackingType.Out);
        var original = gateway.attendance(query).join();
        post("schedules", """
            {"type":"CONTINUOUS","id":"TARDE","name":"Tarde","workingDays":["FRIDAY"],"slot":{"start":"14:00","end":"20:00"}}""");
        post("assignments", """
            {"employeeId":"DEMO","scheduleId":"TARDE","validFrom":"2026-12-01","validThrough":"2026-12-31"}""");

        stop();
        start();
        var history = exchange("GET", "/api/v1/employees/DEMO/assignments", "");
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body()).contains("DIA", "TARDE", "2026-11-30", "2026-12-01");
        assertThat(gateway.attendance(query).join()).isEqualTo(original);
        assertThat(original.incidents().orElseThrow()).containsExactly("LATE_ARRIVAL");
        assertThat(original.balanceMinutes()).isEqualTo(-15);
        var december = gateway.attendance(DayQuery.parse("DEMO", "2026-12-04")).join();
        assertThat(december.expectedMinutes()).isEqualTo(360);
        assertThat(december.incidents().orElseThrow()).containsExactly("ABSENCE");
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
