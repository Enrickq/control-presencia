package software.ulpgc.register.admin.adapter.http;

import com.fasterxml.jackson.databind.*;
import io.javalin.Javalin;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.admin.model.Forms.*;
import software.ulpgc.register.infrastructure.configuration.ServerMain;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AdministrationIntegrationTest {
    @TempDir Path dir;
    private final ObjectMapper json = new ObjectMapper();
    private Javalin server;
    private LocalConnector connector;
    private HttpAdministrationGateway gateway;

    @BeforeEach void start() {
        server = ServerMain.createServer(dir);
        var jetty = server.jettyServer().server();
        connector = new LocalConnector(jetty);
        jetty.addConnector(connector);
        server.start();
        gateway = new HttpAdministrationGateway(URI.create("http://localhost"), request -> {
            var subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                public void onSubscribe(Flow.Subscription sub) { subscriber.onSubscribe(sub); }
                public void onNext(ByteBuffer bytes) { subscriber.onNext(List.of(bytes)); }
                public void onError(Throwable error) { subscriber.onError(error); }
                public void onComplete() { subscriber.onComplete(); }
            });
            return subscriber.getBody().thenApplyAsync(body -> exchange(request.method(), request.uri().getRawPath(), body))
                    .toCompletableFuture();
        });
    }

    @AfterEach void stop() { if (server != null) server.stop(); }

    @Test void employeeAndAssignmentFeedTheDailyReportAndPunchHistory() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var allDays = EnumSet.allOf(DayOfWeek.class);
        var employee = new Employee("DEMO13", "Ángela de prueba");
        gateway.createEmployee(employee).get(5, TimeUnit.SECONDS);
        gateway.createSchedule(Schedule.continuous("DIA", "Jornada", Slot.parse("08:00-16:00"), allDays)).get(5, TimeUnit.SECONDS);
        gateway.assign(new Assignment("DEMO13", "DIA", today.minusDays(1).toString(), today.plusDays(1).toString())).get(5, TimeUnit.SECONDS);
        assertThat(get("employees/DEMO13").path("name").asText()).isEqualTo(employee.name());
        assertThat(get("employees/DEMO13/assignments").get(0).path("scheduleId").asText()).isEqualTo("DIA");
        String body = """
                {"employeeId":"DEMO13","employeeName":"Ángela de prueba","placeId":"SALA","placeName":"Aula","type":"%s"}""";
        var entry = exchange("POST", "/api/v1/punches", body.formatted("IN"));
        var exit = exchange("POST", "/api/v1/punches", body.formatted("OUT"));
        assertThat(entry.statusCode()).isEqualTo(201);
        assertThat(exit.statusCode()).isEqualTo(201);
        LocalDate date = LocalDate.ofInstant(Instant.parse(json.readTree(entry.body()).path("timestamp").asText()), ZoneOffset.UTC);
        String query = "?employeeId=DEMO13&date=" + date;
        var punches = get("punches" + query);
        assertThat(punches.get(0).path("type").asText()).isEqualTo("IN");
        LocalDate exitDate = LocalDate.ofInstant(Instant.parse(json.readTree(exit.body()).path("timestamp").asText()), ZoneOffset.UTC);
        var exitDayPunches = get("punches?employeeId=DEMO13&date=" + exitDate);
        assertThat(exitDayPunches.get(exitDayPunches.size() - 1).path("type").asText()).isEqualTo("OUT");
        assertThat(get("attendance" + query).required("expectedMinutes").asInt()).isEqualTo(480);
    }

    @Test void splitAndShiftSchedulesRoundTripThroughPersistence() throws Exception {
        gateway.createSchedule(Schedule.split("P", "Partido", List.of(Slot.parse("08:00-12:00"),
                Slot.parse("13:00-17:00")), Set.of(DayOfWeek.MONDAY))).join();
        gateway.createSchedule(Schedule.shift("T", "Noche", Map.of(DayOfWeek.FRIDAY, Slot.parse("22:00-06:00")))).join();
        assertThat(get("schedules/P").path("slots").size()).isEqualTo(2);
        assertThat(get("schedules/T").path("weeklySlots").path("FRIDAY").path("end").asText()).isEqualTo("06:00");
    }

    @Test void duplicateAndOverlappingWritesReportConflictsWithoutReplacingData() throws Exception {
        gateway.createEmployee(new Employee("E", "Original")).join();
        assertThatThrownBy(() -> gateway.createEmployee(new Employee("E", "Otro")).join())
                .cause().hasMessageContaining("Ya existe").hasMessageContaining("409");
        gateway.createSchedule(Schedule.continuous("H", "Horario", Slot.parse("08:00-16:00"), Set.of(DayOfWeek.MONDAY))).join();
        var assignment = new Assignment("E", "H", "2026-11-23", "2026-11-30");
        gateway.assign(assignment).join();
        assertThatThrownBy(() -> gateway.assign(assignment).join()).cause().hasMessageContaining("ya tiene una asignación");
        assertThat(get("employees/E").path("name").asText()).isEqualTo("Original");
        assertThat(get("employees/E/assignments").size()).isOne();
    }

    @Test void missingEmployeeIsReportedByTheServer() {
        assertThatThrownBy(() -> gateway.assign(new Assignment("FALTA", "H", "2026-11-23", "2026-11-23")).join())
                .cause().hasMessageContaining("No existe el empleado");
    }

    private JsonNode get(String resource) throws Exception {
        var reply = exchange("GET", "/api/v1/" + resource, "");
        assertThat(reply.statusCode()).isEqualTo(200);
        return json.readTree(reply.body());
    }

    private HttpAdministrationGateway.Reply exchange(String method, String target, String body) {
        try {
            String request = method + " " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                    + "Content-Type: application/json; charset=UTF-8\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                    + "\r\n\r\n" + body;
            var response = connector.getResponse(StandardCharsets.UTF_8.encode(request), 5, TimeUnit.SECONDS);
            String raw = StandardCharsets.UTF_8.decode(response).toString();
            return new HttpAdministrationGateway.Reply(Integer.parseInt(raw.split(" ", 3)[1]),
                    raw.substring(raw.indexOf("\r\n\r\n") + 4));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
