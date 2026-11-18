package software.ulpgc.register.frontend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.ulpgc.register.application.usecase.AssignScheduleToEmployee;
import software.ulpgc.register.application.usecase.ListTrackingEvents;
import software.ulpgc.register.application.usecase.ReadAdministrationData;
import software.ulpgc.register.application.usecase.RegisterEmployee;
import software.ulpgc.register.application.usecase.RegisterSchedule;
import software.ulpgc.register.application.usecase.RegisterTrackingEvent;
import software.ulpgc.register.frontend.adapter.http.HttpPunchGateway;
import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;
import software.ulpgc.register.infrastructure.adapter.in.http.AdministrationHandlers;
import software.ulpgc.register.infrastructure.adapter.in.http.ApiServer;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileEmployeeRepository;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileEventStore;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileScheduleAssignmentRepository;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileScheduleRepository;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PunchIntegrationTest {
    private static final String EMPLOYEE_ID = "EMP-Ñ+ 01";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dataDirectory;

    private Javalin server;
    private HttpClient http;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        startServer();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (http != null) http.close();
    }

    @Test
    void shouldKeepEntryAndExitAndContinueTheSequenceAcrossServerRestarts() throws Exception {
        PunchReceipt entry;
        try (var gateway = new HttpPunchGateway(baseUri())) {
            entry = gateway.register(punch(PunchType.IN)).get(5, SECONDS);
        }
        assertThat(entry.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(entry.placeId()).isEqualTo("CENTRO-01");
        assertThat(entry.type()).isEqualTo(PunchType.IN);

        restartServer();

        PunchReceipt exit;
        try (var gateway = new HttpPunchGateway(baseUri())) {
            exit = gateway.register(punch(PunchType.OUT)).get(5, SECONDS);
        }
        assertThat(exit.type()).isEqualTo(PunchType.OUT);
        assertThat(exit.timestamp()).isAfter(entry.timestamp());

        restartServer();

        var persisted = new ArrayList<JsonNode>();
        var dates = Stream.of(entry, exit)
                .map(receipt -> LocalDate.ofInstant(receipt.timestamp(), ZoneOffset.UTC))
                .distinct().toList();
        for (var date : dates) persisted.addAll(query(date));
        assertThat(persisted).extracting(node -> node.path("type").asText())
                .containsExactly("IN", "OUT");
        assertThat(persisted).extracting(node -> node.path("timestamp").asText())
                .containsExactly(entry.timestamp().toString(), exit.timestamp().toString());
        assertThat(persisted).allSatisfy(node -> {
            assertThat(node.path("employeeId").asText()).isEqualTo(EMPLOYEE_ID);
            assertThat(node.path("placeId").asText()).isEqualTo("CENTRO-01");
        });
    }

    @Test
    void shouldReportDuplicateEntryWithoutWritingAnotherEvent() throws Exception {
        try (var gateway = new HttpPunchGateway(baseUri())) {
            var entry = gateway.register(punch(PunchType.IN)).get(5, SECONDS);
            var duplicate = gateway.register(punch(PunchType.IN));
            assertThatThrownBy(() -> duplicate.get(5, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(PunchException.class,
                            failure -> assertThat(failure.kind()).isEqualTo(PunchException.Kind.CONFLICT));
            assertThat(query(LocalDate.ofInstant(entry.timestamp(), ZoneOffset.UTC))).hasSize(1);
        }
    }

    @Test
    void shouldReportExitWithoutEntryWithoutCreatingEventFiles() throws Exception {
        try (var gateway = new HttpPunchGateway(baseUri())) {
            var orphanExit = gateway.register(punch(PunchType.OUT));
            assertThatThrownBy(() -> orphanExit.get(5, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(PunchException.class,
                            failure -> assertThat(failure.kind()).isEqualTo(PunchException.Kind.CONFLICT));
        }
        assertNoEventFiles();
    }

    @ParameterizedTest(name = "reject controls in {0}: case {index}")
    @MethodSource("unsafeIdentifiers")
    void shouldRejectControlCharactersInRawHttpBeforeTrimmingOrWriting(String field, String value)
            throws Exception {
        var body = JSON.valueToTree(punch(PunchType.IN));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put(field, value);
        var request = HttpRequest.newBuilder(baseUri().resolve("/api/v1/punches"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        var response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(5, SECONDS);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).path("message").asText())
                .isEqualTo(field + " must not contain control characters");
        assertNoEventFiles();
    }

    private static Stream<Arguments> unsafeIdentifiers() {
        return Stream.of("employeeId", "placeId")
                .flatMap(field -> Stream.of("A\tB", "A\nB", "\rA", "A\u007f")
                        .map(value -> Arguments.of(field, value)));
    }

    private void assertNoEventFiles() throws Exception {
        try (var files = Files.walk(dataDirectory)) {
            assertThat(files.filter(path -> path.toString().endsWith(".log")).toList()).isEmpty();
        }
    }

    private List<JsonNode> query(LocalDate date) throws Exception {
        String encodedId = URLEncoder.encode(EMPLOYEE_ID, StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(baseUri().resolve(
                        "/api/v1/punches?employeeId=" + encodedId + "&date=" + date))
                .timeout(Duration.ofSeconds(5)).GET().build();
        var response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(5, SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        var records = new ArrayList<JsonNode>();
        JSON.readTree(response.body()).forEach(records::add);
        return records;
    }

    private static PunchData punch(PunchType type) {
        return new PunchData(EMPLOYEE_ID, "María \"Prueba\"", "CENTRO-01", "Oficina de España", type);
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.port());
    }

    private void restartServer() {
        server.stop();
        startServer();
    }

    private void startServer() {
        var events = new FileEventStore(dataDirectory.toFile());
        var employees = new FileEmployeeRepository(dataDirectory.resolve("employees.jsonl"));
        var schedules = new FileScheduleRepository(dataDirectory.resolve("schedules.jsonl"));
        var assignments = new FileScheduleAssignmentRepository(dataDirectory.resolve("assignments.jsonl"));
        var administration = new AdministrationHandlers(
                new RegisterEmployee(employees),
                new RegisterSchedule(schedules),
                new AssignScheduleToEmployee(employees, schedules, assignments),
                new ReadAdministrationData(employees, schedules, assignments));
        server = ApiServer.create(new RegisterTrackingEvent(events), new ListTrackingEvents(events), administration)
                .start("127.0.0.1", 0);
    }
}
