package software.ulpgc.register.frontend.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class HttpPunchGatewayTest {
    private static final PunchData PUNCH = new PunchData("EMP-1", "José \"Pepe\"", "CENTRO-1", "Oficina Ñ", PunchType.IN);
    private static final String RECEIPT = """
            {"employeeId":"EMP-1","placeId":"CENTRO-1","type":"IN","timestamp":"2026-11-15T09:31:00Z"}
            """;
    private HttpServer server;
    private ExecutorService workers;
    private URI base;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String response = RECEIPT;
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext("/api/v1/punches", exchange -> {
            requests.incrementAndGet();
            requestMethod.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        workers.shutdownNow();
    }

    @Test
    void postsExactContractWithUnicodeAndReadsServerTimestamp() throws Exception {
        try (var gateway = new HttpPunchGateway(URI.create(base + "/"))) {
            var receipt = gateway.register(PUNCH);
            assertThat(receipt.employeeId()).isEqualTo("EMP-1");
            assertThat(receipt.placeId()).isEqualTo("CENTRO-1");
            assertThat(receipt.type()).isEqualTo(PunchType.IN);
            assertThat(receipt.timestamp()).isEqualTo(Instant.parse("2026-11-15T09:31:00Z"));
        }
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(contentType.get()).isEqualTo("application/json; charset=UTF-8");
        var json = new ObjectMapper().readTree(requestBody.get());
        assertThat(json.size()).isEqualTo(5);
        assertThat(json.get("employeeId").textValue()).isEqualTo("EMP-1");
        assertThat(json.get("employeeName").textValue()).isEqualTo("José \"Pepe\"");
        assertThat(json.get("placeId").textValue()).isEqualTo("CENTRO-1");
        assertThat(json.get("placeName").textValue()).isEqualTo("Oficina Ñ");
        assertThat(json.get("type").textValue()).isEqualTo("IN");
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"400,BAD_REQUEST", "404,BAD_REQUEST", "401,BAD_REQUEST", "403,BAD_REQUEST",
            "409,CONFLICT", "500,SERVER_ERROR", "503,SERVER_ERROR", "200,INVALID_RESPONSE", "302,INVALID_RESPONSE"})
    void handlesFailuresEvenWithNonJsonBodyWithoutRetry(int code, PunchException.Kind kind) {
        status = code;
        response = "<html>Error del servidor</html>";
        try (var gateway = new HttpPunchGateway(base)) {
            assertThatThrownBy(() -> gateway.register(PUNCH)).isInstanceOfSatisfying(PunchException.class,
                    error -> assertThat(error.kind()).isEqualTo(kind));
        }
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"Employee is already checked in,Ya hay una entrada abierta",
            "Cannot check out without a previous entry,No hay una entrada abierta"})
    void explainsSequenceConflictsInSpanish(String message, String explanation) throws Exception {
        status = 409;
        response = new ObjectMapper().writeValueAsString(java.util.Map.of("error", "CONFLICT", "message", message));
        try (var gateway = new HttpPunchGateway(base)) {
            assertThatThrownBy(() -> gateway.register(PUNCH)).isInstanceOf(PunchException.class).hasMessageContaining(explanation);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{}", "not json",
            "{\"employeeId\":\"EMP-1\",\"placeId\":\"CENTRO-1\",\"type\":\"IN\",\"timestamp\":\"invalid\"}",
            "{\"employeeId\":\"someone-else\",\"placeId\":\"CENTRO-1\",\"type\":\"IN\",\"timestamp\":\"2026-11-15T09:31:00Z\"}",
            "{\"employeeId\":\"EMP-1\",\"placeId\":\"CENTRO-1\",\"type\":\"OUT\",\"timestamp\":\"2026-11-15T09:31:00Z\"}"})
    void neverReportsSuccessForInvalidOrMismatchedReceipts(String body) {
        response = body;
        try (var gateway = new HttpPunchGateway(base)) {
            assertThatThrownBy(() -> gateway.register(PUNCH)).isInstanceOfSatisfying(PunchException.class, error -> {
                assertThat(error.kind()).isEqualTo(PunchException.Kind.INVALID_RESPONSE);
                assertThat(error).hasMessageContaining("Consulta los registros antes de repetirlo");
            });
        }
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void timesOutWithoutRetryAndWarnsOfUncertainOutcome() {
        delayMillis = 1000;
        try (var gateway = new HttpPunchGateway(base, Duration.ofMillis(100))) {
            assertThatThrownBy(() -> gateway.register(PUNCH)).isInstanceOfSatisfying(PunchException.class, error -> {
                assertThat(error.kind()).isEqualTo(PunchException.Kind.UNAVAILABLE);
                assertThat(error).hasMessageContaining("Consulta los registros antes de repetirlo");
            });
        }
        assertThat(requests.get()).isLessThanOrEqualTo(1);
    }

    @Test
    void explainsConnectionFailure() {
        server.stop(0);
        try (var gateway = new HttpPunchGateway(base)) {
            assertThatThrownBy(() -> gateway.register(PUNCH)).isInstanceOfSatisfying(PunchException.class,
                    error -> assertThat(error.kind()).isEqualTo(PunchException.Kind.UNAVAILABLE));
        }
    }

    @Test
    void preservesInterruption() {
        try (var gateway = new HttpPunchGateway(base)) {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> gateway.register(PUNCH)).isInstanceOf(PunchException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://localhost", "/localhost", "http://localhost?x=1", "http://localhost#x",
            "http://user:secret@localhost", "http://localhost:0", "http://localhost:70000", "file:///tmp"})
    void rejectsInvalidBaseUrls(String url) {
        assertThatThrownBy(() -> new HttpPunchGateway(URI.create(url))).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }
}
